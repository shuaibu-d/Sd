package com.sdtech.studio.render

import android.content.Context
import android.graphics.Bitmap
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
import com.sdtech.studio.performance.CentralBitmapPool
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a [Template] onto a Bitmap. All sizes are relative to a 360-unit reference of the canvas'
 * shorter side, so the output is identical on every phone (it no longer depends on screen density).
 *
 * Uses [CentralBitmapPool] for decoded/tinted logo assets and reuses scratch [Paint]/[Rect]/[RectF]
 * objects across layers to prevent draw-loop allocations.
 */
class TemplateRenderer(private val context: Context) {

    private class RenderScratch {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val overlayPaint = Paint()
        val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val safeZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 70, 70)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        val layerBounds = RectF()
        val srcRectF = RectF()
        val dstRectF = RectF()
        val srcRect = Rect()
    }

    fun render(
        template: Template,
        content: TemplateContent,
        ratio: String,
        theme: TemplateTheme? = template.themes.firstOrNull(),
        options: RenderOptions = RenderOptions(),
        brandKit: BrandKit? = null,
        logos: List<LogoAsset> = emptyList()
    ): Bitmap {
        val baseProfile = RatioProfile.forRatio(ratio)
        val profile = scaleProfileIfNeeded(baseProfile, options.maxDimension)
        val unit = min(profile.width, profile.height) / 360f
        val bitmap = Bitmap.createBitmap(profile.width, profile.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        options.backgroundColor?.let { canvas.drawColor(it) }
        val colors = theme?.colors.orEmpty()
        val darkBackground = isDarkBackground(template, colors)
        val scratch = RenderScratch()

        template.layers.forEach { layer ->
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Template render interrupted")
            }
            boundsFor(layer, ratio, profile, scratch.layerBounds)
            try {
                when (layer) {
                    is TemplateLayer.Background -> drawBackground(canvas, layer, scratch.layerBounds, content, colors, scratch)
                    is TemplateLayer.Shape -> drawShape(canvas, layer, scratch.layerBounds, colors, unit, scratch)
                    is TemplateLayer.Text -> drawText(canvas, layer, scratch.layerBounds, content, colors, unit, scratch)
                    is TemplateLayer.ImageSlot -> drawImageSlot(
                        canvas,
                        layer,
                        scratch.layerBounds,
                        content.images[layer.slot],
                        content.imageFits[layer.slot],
                        content.imageOverlays[layer.slot],
                        scratch
                    )
                    is TemplateLayer.LogoSlot -> drawLogo(canvas, layer, ratio, profile, options, logos, darkBackground, scratch)
                    is TemplateLayer.ContactLine -> drawContact(canvas, layer, scratch.layerBounds, colors, unit, brandKit, scratch)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("TemplateRenderer", "Layer ${layer.id} skipped", error)
            }
        }
        if (options.showSafeZones) drawSafeZones(canvas, profile, scratch)
        return bitmap
    }

    private fun scaleProfileIfNeeded(base: RatioProfile, maxDimension: Int?): RatioProfile {
        val cap = maxDimension?.coerceAtLeast(64) ?: return base
        val longest = max(base.width, base.height)
        if (longest <= cap) return base
        val factor = cap.toFloat() / longest.toFloat()
        return base.copy(
            width = max(1, (base.width * factor).roundToInt()),
            height = max(1, (base.height * factor).roundToInt())
        )
    }

    private fun boundsFor(layer: TemplateLayer, ratio: String, p: RatioProfile, out: RectF) {
        val o = layer.ratioOverrides[ratio]
        val x = o?.x ?: layer.x
        val y = o?.y ?: layer.y
        val w = o?.width ?: layer.width
        val h = o?.height ?: layer.height
        out.set(
            p.width * x / 100f,
            p.height * y / 100f,
            p.width * (x + w) / 100f,
            p.height * (y + h) / 100f
        )
    }

    private fun isDarkBackground(template: Template, colors: Map<String, String>): Boolean {
        val bg = template.layers.filterIsInstance<TemplateLayer.Background>().firstOrNull() ?: return true
        val c = resolveColor(bg.color, colors)
        val luminance = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
        return luminance < 0.5f
    }

    // ------------------------------------------------------------------ drawing
    private fun drawBackground(
        canvas: Canvas,
        layer: TemplateLayer.Background,
        bounds: RectF,
        content: TemplateContent,
        colors: Map<String, String>,
        scratch: RenderScratch
    ) {
        val paint = scratch.fillPaint
        paint.shader = null
        paint.style = Paint.Style.FILL
        when (layer.kind) {
            BackgroundKind.SOLID -> {
                paint.color = resolveColor(layer.color, colors)
                canvas.drawRect(bounds, paint)
            }
            BackgroundKind.GRADIENT -> {
                paint.shader = LinearGradient(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    resolveColor(layer.color, colors),
                    resolveColor(layer.endColor, colors),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(bounds, paint)
                paint.shader = null
            }
            BackgroundKind.IMAGE -> {
                val image = layer.imageAsset?.let { content.images[it] }
                if (image != null) {
                    drawBitmap(canvas, image, bounds, ImageFit.CROP, scratch)
                } else {
                    paint.color = resolveColor(layer.color, colors)
                    canvas.drawRect(bounds, paint)
                }
            }
        }
    }

    private fun drawShape(
        canvas: Canvas,
        layer: TemplateLayer.Shape,
        bounds: RectF,
        colors: Map<String, String>,
        unit: Float,
        scratch: RenderScratch
    ) {
        val paint = scratch.fillPaint
        paint.shader = null
        paint.color = resolveColor(layer.color, colors)
        paint.style = Paint.Style.FILL
        val radius = layer.cornerRadius * unit
        when (layer.shape) {
            ShapeKind.RECTANGLE -> canvas.drawRect(bounds, paint)
            ShapeKind.ROUNDED_RECTANGLE -> canvas.drawRoundRect(bounds, radius, radius, paint)
            ShapeKind.LINE -> {
                paint.strokeWidth = max(1f, layer.strokeWidth * unit)
                canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
            }
            ShapeKind.CIRCLE -> canvas.drawOval(bounds, paint)
        }
        layer.strokeColor?.let {
            val stroke = scratch.strokePaint
            stroke.color = resolveColor(it, colors)
            stroke.strokeWidth = max(1f, layer.strokeWidth * unit)
            if (layer.shape == ShapeKind.CIRCLE) {
                canvas.drawOval(bounds, stroke)
            } else {
                canvas.drawRoundRect(bounds, radius, radius, stroke)
            }
        }
    }

    private fun drawText(
        canvas: Canvas,
        layer: TemplateLayer.Text,
        bounds: RectF,
        content: TemplateContent,
        colors: Map<String, String>,
        unit: Float,
        scratch: RenderScratch
    ) {
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
            val first = buildLayout(text, width, size, typeface, color, align, Int.MAX_VALUE, scratch.textPaint)
            if (first.lineCount > layer.maxLines || first.height > bounds.height()) {
                var lo = 8f * unit
                var hi = size
                var best = lo
                repeat(9) {
                    val mid = (lo + hi) / 2f
                    val test = buildLayout(text, width, mid, typeface, color, align, Int.MAX_VALUE, scratch.textPaint)
                    if (test.lineCount <= layer.maxLines && test.height <= bounds.height()) {
                        best = mid
                        lo = mid
                    } else {
                        hi = mid
                    }
                }
                size = best
            }
        }
        val layout = buildLayout(text, width, size, typeface, color, align, layer.maxLines, scratch.textPaint)
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(
        text: String,
        width: Int,
        size: Float,
        typeface: Typeface,
        color: Int,
        alignment: Layout.Alignment,
        maxLines: Int,
        paint: TextPaint
    ): StaticLayout {
        paint.textSize = size
        paint.typeface = typeface
        paint.color = color
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
        if (maxLines != Int.MAX_VALUE) {
            builder.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        }
        return builder.build()
    }

    private fun drawContact(
        canvas: Canvas,
        layer: TemplateLayer.ContactLine,
        bounds: RectF,
        colors: Map<String, String>,
        unit: Float,
        brandKit: BrandKit?,
        scratch: RenderScratch
    ) {
        val parts = buildList {
            if (layer.includeWebsite && !brandKit?.website.isNullOrBlank()) add(brandKit!!.website)
            if (layer.includeWhatsapp && !brandKit?.whatsapp.isNullOrBlank()) add(brandKit!!.whatsapp)
            if (layer.includeHandle && !brandKit?.handle.isNullOrBlank()) add(brandKit!!.handle)
        }
        if (parts.isEmpty()) return
        val text = parts.joinToString("   \u2022   ")
        val paint = scratch.contactPaint
        paint.color = resolveColor(layer.color, colors)
        paint.typeface = typeface(FontFamily.BODY, false)
        paint.textSize = layer.sizeSp * unit
        while (paint.measureText(text) > bounds.width() && paint.textSize > 6f * unit) {
            paint.textSize -= 0.5f * unit
        }
        canvas.drawText(text, bounds.left, bounds.top - paint.ascent(), paint)
    }

    private fun drawImageSlot(
        canvas: Canvas,
        layer: TemplateLayer.ImageSlot,
        bounds: RectF,
        image: Bitmap?,
        fitOverride: ImageFit?,
        overlayOverride: Float?,
        scratch: RenderScratch
    ) {
        if (image == null || image.isRecycled) {
            drawEmptyImageSlotPlaceholder(canvas, layer, bounds, scratch)
            return
        }
        drawBitmap(canvas, image, bounds, fitOverride ?: layer.fit, scratch)
        val overlay = overlayOverride ?: layer.darkOverlay
        if (overlay > 0f) {
            val alpha = (overlay.coerceIn(0f, 1f) * 255).toInt()
            scratch.overlayPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawRect(bounds, scratch.overlayPaint)
        }
    }

    private fun drawEmptyImageSlotPlaceholder(
        canvas: Canvas,
        layer: TemplateLayer.ImageSlot,
        bounds: RectF,
        scratch: RenderScratch
    ) {
        val paint = scratch.fillPaint
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            Color.rgb(16, 28, 44),
            Color.rgb(26, 48, 72),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(bounds, paint)
        paint.shader = null

        // Subtle studio diagonal accent glow
        paint.color = Color.argb(38, 22, 166, 160)
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val r = min(bounds.width(), bounds.height()) * 0.36f
        canvas.drawCircle(cx, cy, r, paint)

        val stroke = scratch.strokePaint
        stroke.color = Color.argb(90, 217, 180, 91)
        stroke.strokeWidth = max(1.5f, min(bounds.width(), bounds.height()) * 0.01f)
        val inset = min(bounds.width(), bounds.height()) * 0.06f
        scratch.dstRectF.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        canvas.drawRoundRect(scratch.dstRectF, inset * 0.5f, inset * 0.5f, stroke)

        val label = layer.slot.uppercase()
        val tp = scratch.contactPaint
        tp.color = Color.argb(180, 243, 245, 248)
        tp.typeface = typeface(FontFamily.MONO, true)
        tp.textSize = (min(bounds.width(), bounds.height()) * 0.075f).coerceIn(10f, 28f)
        val textWidth = tp.measureText(label)
        canvas.drawText(label, cx - textWidth / 2f, cy - (tp.ascent() + tp.descent()) / 2f, tp)
    }

    private fun drawBitmap(canvas: Canvas, bitmap: Bitmap, bounds: RectF, fit: ImageFit, scratch: RenderScratch) {
        if (bitmap.isRecycled) return
        val source = scratch.srcRectF
        val dest = scratch.dstRectF
        source.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        dest.set(bounds)
        if (fit == ImageFit.FIT) {
            val scale = min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            dest.set(bounds.centerX() - w / 2, bounds.centerY() - h / 2, bounds.centerX() + w / 2, bounds.centerY() + h / 2)
        } else {
            val scale = max(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val cropW = bounds.width() / scale
            val cropH = bounds.height() / scale
            source.set(
                (bitmap.width - cropW) / 2,
                (bitmap.height - cropH) / 2,
                (bitmap.width + cropW) / 2,
                (bitmap.height + cropH) / 2
            )
        }
        scratch.srcRect.set(
            source.left.toInt(),
            source.top.toInt(),
            source.right.toInt(),
            source.bottom.toInt()
        )
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, scratch.srcRect, dest, scratch.bitmapPaint)
        canvas.restore()
    }

    // ------------------------------------------------------------------ logo
    private fun drawLogo(
        canvas: Canvas,
        layer: TemplateLayer.LogoSlot,
        ratio: String,
        profile: RatioProfile,
        options: RenderOptions,
        logos: List<LogoAsset>,
        darkBackground: Boolean,
        scratch: RenderScratch
    ) {
        val variant = (options.logoTypeOverride ?: layer.logoType).uppercase().let { if (it == "AUTO") "ICON" else it }
        val asset = logos.lastOrNull { it.type.name == variant }
            ?: logos.lastOrNull { it.type.name == "FULL" }
            ?: logos.lastOrNull { it.type.name == "ICON" }

        val explicitVariant = asset?.type?.name == "LIGHT" || asset?.type?.name == "DARK"
        val tint = !darkBackground && !explicitVariant
        val logo = if (asset != null) {
            resolveLogoBitmap(asset.filePath, tint)
        } else {
            resolveBundledLogoBitmap(variant, darkBackground)
        } ?: return
        if (logo.isRecycled) return
        val aspect = logo.width.toFloat() / logo.height.coerceAtLeast(1)
        val lockup = (asset?.type?.name ?: variant) != "ICON"
        val mode = options.logoPlacementMode ?: layer.placement
        val base = layer.placementOverrides[ratio]?.let { o ->
            val w = profile.width * o.width / 100f
            LogoPlacement(
                profile.width * (100f - o.rightMargin) / 100f - w,
                profile.height * o.topMargin / 100f,
                w,
                w / aspect
            )
        } ?: LogoPlacementRules.forProfile(profile, mode, aspect)
        val placement = base.scaledFromTopRight(if (lockup) LOCKUP_FACTOR else 1f)
        val logoBounds = RectF(placement.x, placement.y, placement.x + placement.width, placement.y + placement.height)
        drawBitmap(canvas, logo, logoBounds, ImageFit.FIT, scratch)
    }

    private fun resolveLogoBitmap(path: String, tint: Boolean): Bitmap? {
        val file = File(path)
        if (!file.isFile) return null
        val cacheKey = "logo:${file.absolutePath}:${file.lastModified()}:tint=$tint"
        return CentralBitmapPool.getOrPut(cacheKey) {
            val raw = CentralBitmapPool.decodeFileCached(file.absolutePath, maxEdgePx = 1024) ?: return@getOrPut null
            if (tint) tintLightPixels(raw) else raw
        }
    }

    private fun resolveBundledLogoBitmap(variant: String, darkBackground: Boolean): Bitmap? {
        val assetFileName = when (variant) {
            "ICON" -> if (darkBackground) "logo_icon_light.png" else "logo_icon_dark.png"
            "LIGHT" -> "logo_full_dark.png"
            "DARK" -> "logo_full_light.png"
            else -> if (darkBackground) "logo_full_light.png" else "logo_full_dark.png"
        }
        val cacheKey = "bundled_logo:$assetFileName"
        return CentralBitmapPool.getOrPut(cacheKey) {
            runCatching {
                context.assets.open("brand/$assetFileName").use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
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

    private fun drawSafeZones(canvas: Canvas, p: RatioProfile, scratch: RenderScratch) {
        canvas.drawRect(
            p.width * p.safeLeft,
            p.height * p.safeTop,
            p.width * (1f - p.safeRight),
            p.height * (1f - p.safeBottom),
            scratch.safeZonePaint
        )
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

    private companion object {
        const val LOCKUP_FACTOR = 1.7f
        val PLACEHOLDER = Regex("\\{([A-Za-z0-9_.-]+)}")
        val TYPEFACES = ConcurrentHashMap<Int, Typeface>()
    }
}
