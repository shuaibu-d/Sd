package com.sdtech.studio.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.sdtech.studio.R
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.LogoAsset
import java.io.File
import kotlin.math.max
import kotlin.math.min

class TemplateRenderer(private val context: Context) {
    private val density = context.resources.displayMetrics.density

    fun render(
        template: Template,
        content: TemplateContent,
        ratio: String,
        theme: TemplateTheme? = template.themes.firstOrNull(),
        options: RenderOptions = RenderOptions(),
        brandKit: BrandKit? = null,
        logos: List<LogoAsset> = emptyList()
    ): Bitmap {
        val profile = RatioProfile.forRatio(ratio)
        val bitmap = Bitmap.createBitmap(profile.width, profile.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        options.backgroundColor?.let { canvas.drawColor(it) }
        val themeColors = theme?.colors.orEmpty()
        val logoCache = mutableMapOf<String, Bitmap?>()

        template.layers.forEach { layer ->
            val bounds = layer.ratioOverrides[ratio]?.let {
                RectF(profile.width * it.x / 100f, profile.height * it.y / 100f,
                    profile.width * (it.x + it.width) / 100f, profile.height * (it.y + it.height) / 100f)
            } ?: RectF(profile.width * layer.x / 100f, profile.height * layer.y / 100f,
                profile.width * (layer.x + layer.width) / 100f, profile.height * (layer.y + layer.height) / 100f)
            when (layer) {
                is TemplateLayer.Background -> drawBackground(canvas, layer, bounds, content, themeColors)
                is TemplateLayer.Shape -> drawShape(canvas, layer, bounds, resolveColor(layer.color, themeColors))
                is TemplateLayer.Text -> drawText(canvas, layer, bounds, content, themeColors, options)
                is TemplateLayer.ImageSlot -> drawImage(canvas, layer, bounds, content.images[layer.slot], content.imageFits[layer.slot], content.imageOverlays[layer.slot])
                is TemplateLayer.LogoSlot -> {
                    val asset = logos.lastOrNull { it.type.name.equals(options.logoTypeOverride ?: layer.logoType, true) }
                        ?: logos.lastOrNull { it.type.name == "FULL" }
                    val logo = asset?.let { logoCache.getOrPut(it.filePath) { decodeFile(it.filePath) } }
                    if (logo != null) {
                        val placement = layer.placementOverrides[ratio]?.let { override ->
                            LogoPlacement(
                                profile.width * (100f - override.rightMargin - override.width) / 100f,
                                profile.height * override.topMargin / 100f,
                                profile.width * override.width / 100f,
                                profile.width * override.width / 100f * logo.height / logo.width
                            )
                        } ?: LogoPlacementRules.forRatio(ratio, (options.logoPlacementMode ?: layer.placement), logo.width.toFloat() / logo.height)
                        drawImage(canvas, logo, RectF(placement.x, placement.y,
                            placement.x + placement.width, placement.y + placement.height), ImageFit.FIT)
                    }
                }
                is TemplateLayer.ContactLine -> {
                    val parts = buildList {
                        if (layer.includeWebsite && !brandKit?.website.isNullOrBlank()) add(brandKit!!.website)
                        if (layer.includeWhatsapp && !brandKit?.whatsapp.isNullOrBlank()) add(brandKit!!.whatsapp)
                        if (layer.includeHandle && !brandKit?.handle.isNullOrBlank()) add(brandKit!!.handle)
                    }
                    drawSimpleText(canvas, parts.joinToString("   •   "), bounds,
                        resolveColor(layer.color, themeColors), layer.sizeSp, Typeface.create("sans-serif", Typeface.NORMAL))
                }
            }
        }
        if (options.showSafeZones) drawSafeZones(canvas, profile)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, layer: TemplateLayer.Background, bounds: RectF,
                               content: TemplateContent, colors: Map<String, String>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (layer.kind) {
            BackgroundKind.SOLID -> { paint.color = resolveColor(layer.color, colors); canvas.drawRect(bounds, paint) }
            BackgroundKind.GRADIENT -> {
                paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    resolveColor(layer.color, colors), resolveColor(layer.endColor, colors), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, paint); paint.shader = null
            }
            BackgroundKind.IMAGE -> {
                val image = layer.imageAsset?.let { content.images[it] }
                if (image != null) drawImage(canvas, image, bounds, ImageFit.CROP)
                else { paint.color = resolveColor(layer.color, colors); canvas.drawRect(bounds, paint) }
            }
        }
    }

    private fun drawShape(canvas: Canvas, layer: TemplateLayer.Shape, bounds: RectF, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        when (layer.shape) {
            ShapeKind.RECTANGLE -> canvas.drawRect(bounds, paint)
            ShapeKind.ROUNDED_RECTANGLE -> canvas.drawRoundRect(bounds, layer.cornerRadius * density, layer.cornerRadius * density, paint)
            ShapeKind.LINE -> { paint.strokeWidth = max(1f, layer.strokeWidth * density); canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint) }
            ShapeKind.CIRCLE -> canvas.drawOval(bounds, paint)
        }
        layer.strokeColor?.let {
            paint.color = Color.parseColor(it); paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, layer.strokeWidth * density)
            if (layer.shape == ShapeKind.CIRCLE) canvas.drawOval(bounds, paint) else canvas.drawRect(bounds, paint)
        }
    }

    private fun drawText(canvas: Canvas, layer: TemplateLayer.Text, bounds: RectF,
                         content: TemplateContent, colors: Map<String, String>, options: RenderOptions) {
        val resolved = content.values[layer.id] ?: Regex("\\{([A-Za-z0-9_.-]+)}").replace(layer.text) { match ->
            content.values[match.groupValues[1]] ?: match.value
        }
        val safeResolved = resolved.take(4_000)
        val preset = content.textPresets[layer.id] ?: when (layer.role) {
            TextRole.HEADING -> "HEADING"
            TextRole.BODY -> "BODY"
            TextRole.LABEL, TextRole.CTA -> "LABEL"
        }
        val presetFont = if (content.brandLock) when (preset) {
            "HEADING" -> FontFamily.HEADING
            "BODY" -> FontFamily.BODY
            "LABEL" -> FontFamily.MONO
            else -> layer.font
        } else layer.font
        val presetColor = if (content.brandLock) when (preset) {
            "HEADING" -> options.brandColors.entries.firstOrNull { it.key.contains("navy", true) || it.key.contains("primary", true) }?.value
                ?: colors[if (colors.containsKey("ink")) "ink" else "navy"]
            "BODY" -> options.brandColors.entries.firstOrNull { it.key.contains("muted", true) || it.key.contains("secondary", true) }?.value ?: colors["muted"]
            "LABEL" -> options.brandColors.entries.firstOrNull { it.key.contains("teal", true) || it.key.contains("accent", true) }?.value ?: colors["teal"]
            else -> null
        } else null
        val typeface = typeface(presetFont, layer.bold || preset == "HEADING")
        val align = when (layer.alignment) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val availableWidth = max(1, bounds.width().toInt())
        val presetScale = if (content.brandLock) when (preset) {
            "HEADING" -> 1.08f
            "BODY" -> 0.96f
            "LABEL" -> 0.82f
            else -> 1f
        } else 1f
        val baseSize = layer.sizeSp * density * presetScale
        var chosenSize = baseSize
        var layout = createLayout(safeResolved, availableWidth, chosenSize, typeface,
            (presetColor?.let { Color.parseColor(it) } ?: resolveColor(layer.color, colors)), align, layer.maxLines)
        if (layer.autoFit) {
            while ((layout.lineCount > layer.maxLines || layout.height > bounds.height()) && chosenSize > 8f * density) {
                chosenSize -= max(1f, density)
                layout = createLayout(safeResolved, availableWidth, chosenSize, typeface,
                    (presetColor?.let { Color.parseColor(it) } ?: resolveColor(layer.color, colors)), align, layer.maxLines)
            }
        }
        canvas.save(); canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        canvas.translate(bounds.left, bounds.top); layout.draw(canvas); canvas.restore()
    }

    private fun createLayout(text: String, width: Int, size: Float, typeface: Typeface,
                             color: Int, alignment: Layout.Alignment, maxLines: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.typeface = typeface; this.color = color
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment).setIncludePad(false).setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    }

    private fun drawSimpleText(canvas: Canvas, text: String, bounds: RectF, color: Int,
                               sizeSp: Float, typeface: Typeface) {
        if (text.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = sizeSp * density; this.typeface = typeface
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, bounds.width().toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(1).build()
        canvas.save(); canvas.clipRect(bounds); canvas.translate(bounds.left, bounds.top); layout.draw(canvas); canvas.restore()
    }

    private fun drawImage(canvas: Canvas, layer: TemplateLayer.ImageSlot, bounds: RectF, image: Bitmap?, fitOverride: ImageFit? = null, overlayOverride: Float? = null) {
        if (image == null) return
        drawImage(canvas, image, bounds, fitOverride ?: layer.fit)
        val overlay = overlayOverride ?: layer.darkOverlay
        if (overlay > 0f) {
            val alpha = (overlay.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawRect(bounds, Paint().apply { color = Color.argb(alpha, 0, 0, 0) })
        }
    }

    private fun drawImage(canvas: Canvas, bitmap: Bitmap, bounds: RectF, fit: ImageFit) {
        val source = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val dest = RectF(bounds)
        if (fit == ImageFit.FIT) {
            val scale = min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val w = bitmap.width * scale; val h = bitmap.height * scale
            dest.set(bounds.centerX() - w / 2, bounds.centerY() - h / 2,
                bounds.centerX() + w / 2, bounds.centerY() + h / 2)
        } else {
            val scale = max(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val cropW = bounds.width() / scale; val cropH = bounds.height() / scale
            source.set((bitmap.width - cropW) / 2, (bitmap.height - cropH) / 2,
                (bitmap.width + cropW) / 2, (bitmap.height + cropH) / 2)
        }
        canvas.save(); canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, android.graphics.Rect(source.left.toInt(), source.top.toInt(), source.right.toInt(), source.bottom.toInt()), dest,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private fun drawSafeZones(canvas: Canvas, p: RatioProfile) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 70, 70); style = Paint.Style.STROKE; strokeWidth = 3f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        canvas.drawRect(p.width * p.safeLeft, p.height * p.safeTop,
            p.width * (1f - p.safeRight), p.height * (1f - p.safeBottom), paint)
    }

    private fun resolveColor(value: String, colors: Map<String, String>): Int =
        Color.parseColor(colors[value.removePrefix("\$")] ?: value)

    private fun typeface(family: FontFamily, bold: Boolean): Typeface {
        val resource = when (family) {
            FontFamily.HEADING -> if (bold) R.font.space_grotesk_bold else R.font.space_grotesk_semibold
            FontFamily.BODY -> if (bold) R.font.inter_bold else R.font.inter_regular
            FontFamily.MONO -> if (bold) R.font.jetbrains_mono_bold else R.font.jetbrains_mono_regular
        }
        return context.resources.getFont(resource)
    }

    private fun decodeFile(path: String): Bitmap? = runCatching {
        val file = File(path)
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()
}
