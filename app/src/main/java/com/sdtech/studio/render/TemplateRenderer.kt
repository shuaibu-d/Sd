package com.sdtech.studio.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.sdtech.studio.R
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.LogoAsset
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [Template] onto a Bitmap. All sizes are relative to a 360-unit reference of the canvas'
 * shorter side, so the output is identical on every phone (it no longer depends on screen density).
 */
class TemplateRenderer(private val context: Context) {

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
        val unit = min(profile.width, profile.height) / 360f
        val bitmap = Bitmap.createBitmap(profile.width, profile.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        options.backgroundColor?.let { canvas.drawColor(it) }
        val colors = theme?.colors.orEmpty()
        val logoCache = HashMap<String, Bitmap?>()
        val darkBackground = isDarkBackground(template, colors)

        template.layers.forEach { layer ->
            val bounds = boundsFor(layer, ratio, profile)
            try {
                when (layer) {
                    is TemplateLayer.Background -> drawBackground(canvas, layer, bounds, content, colors)
                    is TemplateLayer.Shape -> drawShape(canvas, layer, bounds, colors, unit)
                    is TemplateLayer.Text -> drawText(canvas, layer, bounds, content, colors, unit)
                    is TemplateLayer.ImageSlot -> drawImageSlot(canvas, layer, bounds, content.images[layer.slot],
                        content.imageFits[layer.slot], content.imageOverlays[layer.slot])
                    is TemplateLayer.LogoSlot -> drawLogo(canvas, layer, ratio, profile, options, logos, darkBackground, logoCache)
                    is TemplateLayer.ContactLine -> drawContact(canvas, layer, bounds, colors, unit, brandKit)
                }
            } catch (error: Exception) {
                android.util.Log.w("TemplateRenderer", "Layer ${layer.id} skipped", error)
            }
        }
        if (options.showSafeZones) drawSafeZones(canvas, profile)
        return bitmap
    }

    private fun boundsFor(layer: TemplateLayer, ratio: String, p: RatioProfile): RectF {
        val o = layer.ratioOverrides[ratio]
        val x = o?.x ?: layer.x
        val y = o?.y ?: layer.y
        val w = o?.width ?: layer.width
        val h = o?.height ?: layer.height
        return RectF(p.width * x / 100f, p.height * y / 100f, p.width * (x + w) / 100f, p.height * (y + h) / 100f)
    }

    private fun isDarkBackground(template: Template, colors: Map<String, String>): Boolean {
        val bg = template.layers.filterIsInstance<TemplateLayer.Background>().firstOrNull() ?: return true
        val c = resolveColor(bg.color, colors)
        val luminance = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        return luminance < 0.5f
    }

    // ------------------------------------------------------------------ drawing
    private fun drawBackground(canvas: Canvas, layer: TemplateLayer.Background, bounds: RectF,
                               content: TemplateContent, colors: Map<String, String>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (layer.kind) {
            BackgroundKind.SOLID -> { paint.color = resolveColor(layer.color, colors); canvas.drawRect(bounds, paint) }
            BackgroundKind.GRADIENT -> {
                paint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    resolveColor(layer.color, colors), resolveColor(layer.endColor, colors), Shader.TileMode.CLAMP)
                canvas.drawRect(bounds, paint)
            }
            BackgroundKind.IMAGE -> {
                val image = layer.imageAsset?.let { content.images[it] }
                if (image != null) drawBitmap(canvas, image, bounds, ImageFit.CROP)
                else { paint.color = resolveColor(layer.color, colors); canvas.drawRect(bounds, paint) }
            }
        }
    }

    private fun drawShape(canvas: Canvas, layer: TemplateLayer.Shape, bounds: RectF, colors: Map<String, String>, unit: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = resolveColor(layer.color, colors); style = Paint.Style.FILL }
        val radius = layer.cornerRadius * unit
        when (layer.shape) {
            ShapeKind.RECTANGLE -> canvas.drawRect(bounds, paint)
            ShapeKind.ROUNDED_RECTANGLE -> canvas.drawRoundRect(bounds, radius, radius, paint)
            ShapeKind.LINE -> { paint.strokeWidth = max(1f, layer.strokeWidth * unit); canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint) }
            ShapeKind.CIRCLE -> canvas.drawOval(bounds, paint)
        }
        layer.strokeColor?.let {
            paint.color = resolveColor(it, colors); paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, layer.strokeWidth * unit)
            if (layer.shape == ShapeKind.CIRCLE) canvas.drawOval(bounds, paint) else canvas.drawRoundRect(bounds, radius, radius, paint)
        }
    }

    private fun drawText(canvas: Canvas, layer: TemplateLayer.Text, bounds: RectF, content: TemplateContent,
                         colors: Map<String, String>, unit: Float) {
        val text = (content.values[layer.id]
            ?: PLACEHOLDER.replace(layer.text) { m -> content.values[m.groupValues[1]] ?: m.value }).take(4_000)
        if (text.isBlank()) return
        val preset = content.textPresets[layer.id]
        val family = when (preset) {
            "HEADING" -> FontFamily.HEADING
            "BODY" -> FontFamily.BODY
            "LABEL" -> FontFamily.MONO
            else -> layer.font
        }
        val bold = layer.bold || preset == "HEADING"
        val color = when (preset) {
            "HEADING" -> colors["ink"]?.let { safeColor(it) }
            "BODY" -> colors["muted"]?.let { safeColor(it) }
            "LABEL" -> colors["teal"]?.let { safeColor(it) }
            else -> null
        } ?: resolveColor(layer.color, colors)
        val scale = when (preset) { "HEADING" -> 1.08f; "BODY" -> 0.96f; "LABEL" -> 0.82f; else -> 1f }
        val typeface = typeface(family, bold)
        val align = when (layer.alignment) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val width = max(1, bounds.width().toInt())
        var size = layer.sizeSp * unit * scale
        if (layer.autoFit) {
            val first = buildLayout(text, width, size, typeface, color, align, Int.MAX_VALUE)
            if (first.lineCount > layer.maxLines || first.height > bounds.height()) {
                var lo = 8f * unit
                var hi = size
                var best = lo
                repeat(9) {
                    val mid = (lo + hi) / 2f
                    val test = buildLayout(text, width, mid, typeface, color, align, Int.MAX_VALUE)
                    if (test.lineCount <= layer.maxLines && test.height <= bounds.height()) { best = mid; lo = mid } else hi = mid
                }
                size = best
            }
        }
        val layout = buildLayout(text, width, size, typeface, color, align, layer.maxLines)
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(text: String, width: Int, size: Float, typeface: Typeface, color: Int,
                            alignment: Layout.Alignment, maxLines: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.typeface = typeface; this.color = color }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment).setIncludePad(false)
        if (maxLines != Int.MAX_VALUE) builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        return builder.build()
    }

    private fun drawContact(canvas: Canvas, layer: TemplateLayer.ContactLine, bounds: RectF,
                            colors: Map<String, String>, unit: Float, brandKit: BrandKit?) {
        val parts = buildList {
            if (layer.includeWebsite && !brandKit?.website.isNullOrBlank()) add(brandKit!!.website)
            if (layer.includeWhatsapp && !brandKit?.whatsapp.isNullOrBlank()) add(brandKit!!.whatsapp)
            if (layer.includeHandle && !brandKit?.handle.isNullOrBlank()) add(brandKit!!.handle)
        }
        if (parts.isEmpty()) return
        val text = parts.joinToString("   \u2022   ")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resolveColor(layer.color, colors)
            typeface = typeface(FontFamily.BODY, false)
            textSize = layer.sizeSp * unit
        }
        while (paint.measureText(text) > bounds.width() && paint.textSize > 6f * unit) paint.textSize -= 0.5f * unit
        canvas.drawText(text, bounds.left, bounds.top - paint.ascent(), paint)
    }

    private fun drawImageSlot(canvas: Canvas, layer: TemplateLayer.ImageSlot, bounds: RectF, image: Bitmap?,
                              fitOverride: ImageFit?, overlayOverride: Float?) {
        if (image == null || image.isRecycled) return
        drawBitmap(canvas, image, bounds, fitOverride ?: layer.fit)
        val overlay = overlayOverride ?: layer.darkOverlay
        if (overlay > 0f) {
            val alpha = (overlay.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawRect(bounds, Paint().apply { color = Color.argb(alpha, 0, 0, 0) })
        }
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, bounds: RectF, fit: ImageFit) {
        val source = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val dest = RectF(bounds)
        if (fit == ImageFit.FIT) {
            val scale = min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            dest.set(bounds.centerX() - w / 2, bounds.centerY() - h / 2, bounds.centerX() + w / 2, bounds.centerY() + h / 2)
        } else {
            val scale = max(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val cropW = bounds.width() / scale
            val cropH = bounds.height() / scale
            source.set((bitmap.width - cropW) / 2, (bitmap.height - cropH) / 2, (bitmap.width + cropW) / 2, (bitmap.height + cropH) / 2)
        }
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, Rect(source.left.toInt(), source.top.toInt(), source.right.toInt(), source.bottom.toInt()),
            dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    // ------------------------------------------------------------------ logo
    private fun drawLogo(canvas: Canvas, layer: TemplateLayer.LogoSlot, ratio: String, profile: RatioProfile,
                         options: RenderOptions, logos: List<LogoAsset>, darkBackground: Boolean,
                         cache: MutableMap<String, Bitmap?>) {
        val variant = (options.logoTypeOverride ?: layer.logoType).uppercase().let { if (it == "AUTO") "ICON" else it }
        val asset = logos.lastOrNull { it.type.name == variant }
            ?: logos.lastOrNull { it.type.name == "FULL" }
            ?: logos.lastOrNull { it.type.name == "ICON" }
            ?: return
        val explicitVariant = asset.type.name == "LIGHT" || asset.type.name == "DARK"
        val tint = !darkBackground && !explicitVariant
        val logo = cache.getOrPut("${asset.filePath}|$tint") {
            decodeFile(asset.filePath)?.let { if (tint) tintLightPixels(it) else it }
        } ?: return
        val aspect = logo.width.toFloat() / logo.height
        val lockup = asset.type.name != "ICON"
        val mode = options.logoPlacementMode ?: layer.placement
        val base = layer.placementOverrides[ratio]?.let { o ->
            val w = profile.width * o.width / 100f
            LogoPlacement(profile.width * (100f - o.rightMargin) / 100f - w, profile.height * o.topMargin / 100f, w, w / aspect)
        } ?: LogoPlacementRules.forRatio(ratio, mode, aspect)
        val placement = base.scaledFromTopRight(if (lockup) LOCKUP_FACTOR else 1f)
        drawBitmap(canvas, logo, RectF(placement.x, placement.y, placement.x + placement.width, placement.y + placement.height), ImageFit.FIT)
    }

    /** Light logo parts (white) would vanish on light backgrounds, so they are recoloured to brand navy. */
    private fun tintLightPixels(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = c ushr 24
            if (a == 0) continue
            if (((c shr 16) and 255) > 200 && ((c shr 8) and 255) > 200 && (c and 255) > 200) {
                pixels[i] = (a shl 24) or 0x101C2C
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun drawSafeZones(canvas: Canvas, p: RatioProfile) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 70, 70); style = Paint.Style.STROKE; strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        canvas.drawRect(p.width * p.safeLeft, p.height * p.safeTop, p.width * (1f - p.safeRight), p.height * (1f - p.safeBottom), paint)
    }

    // ------------------------------------------------------------------ helpers
    private fun resolveColor(value: String, colors: Map<String, String>): Int =
        safeColor(colors[value.removePrefix("\$")] ?: value) ?: Color.MAGENTA

    private fun safeColor(value: String): Int? = runCatching { Color.parseColor(value) }.getOrNull()

    private fun typeface(family: FontFamily, bold: Boolean): Typeface {
        val resource = when (family) {
            FontFamily.HEADING -> if (bold) R.font.space_grotesk_bold else R.font.space_grotesk_semibold
            FontFamily.BODY -> if (bold) R.font.inter_bold else R.font.inter_regular
            FontFamily.MONO -> if (bold) R.font.jetbrains_mono_bold else R.font.jetbrains_mono_regular
        }
        return TYPEFACES.getOrPut(resource) {
            runCatching { context.resources.getFont(resource) }.getOrDefault(Typeface.DEFAULT)
        }
    }

    private fun decodeFile(path: String): Bitmap? = runCatching {
        val file = File(path)
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }.getOrNull()

    private companion object {
        const val LOCKUP_FACTOR = 1.7f
        val PLACEHOLDER = Regex("\\{([A-Za-z0-9_.-]+)}")
        val TYPEFACES = ConcurrentHashMap<Int, Typeface>()
    }
}
