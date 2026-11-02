@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sdtech.studio.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.effect.CanvasOverlay

/**
 * Time-aware, full-frame overlay for burned captions and the slim export progress bar.
 * Uses CanvasOverlay's presentation timestamp rather than allocating one bitmap per frame.
 */
internal class ExportPolishOverlay(
    private val width: Int,
    private val height: Int,
    private val scenes: List<String>,
    durationsMs: List<Long>,
    brandAccent: String,
    private val captionsEnabled: Boolean,
    private val progressEnabled: Boolean
) : CanvasOverlay(false) {
    private val sceneEndsUs = durationsMs.runningFold(0L) { total, value ->
        total + value.coerceAtLeast(0L) * 1_000L
    }.drop(1)
    private val totalDurationUs = sceneEndsUs.lastOrNull()?.coerceAtLeast(1L) ?: 1L
    private val accent = parseColor(brandAccent, Color.rgb(21, 155, 152))
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(218, 8, 22, 28)
        style = Paint.Style.FILL
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = (height * 0.026f).coerceIn(24f, 54f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(85, 255, 255, 255)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (captionsEnabled) drawCaption(canvas, presentationTimeUs.coerceAtLeast(0L))
        if (progressEnabled) drawProgress(canvas, presentationTimeUs.coerceAtLeast(0L))
    }

    private fun drawCaption(canvas: Canvas, timeUs: Long) {
        val sceneIndex = sceneEndsUs.indexOfFirst { timeUs < it }.let { if (it < 0) scenes.lastIndex else it }
        val caption = scenes.getOrNull(sceneIndex)?.trim().orEmpty()
        if (caption.isBlank()) return

        val maxTextWidth = (width * 0.82f).toInt().coerceAtLeast(120)
        val horizontalPadding = (width * 0.035f).coerceAtLeast(18f)
        val verticalPadding = (height * 0.012f).coerceAtLeast(10f)
        val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val boxWidth = minOf(width * 0.90f, layout.width + horizontalPadding * 2f)
        val boxHeight = layout.height + verticalPadding * 2f
        // 15% bottom safe margin keeps captions above platform UI and the progress indicator.
        val left = (width - boxWidth) / 2f
        val top = height * 0.75f - boxHeight / 2f
        val rect = RectF(left, top, left + boxWidth, top + boxHeight)
        canvas.drawRoundRect(rect, height * 0.012f, height * 0.012f, backgroundPaint)
        canvas.drawRoundRect(
            RectF(rect.left, rect.top, rect.left + (width * 0.009f).coerceAtLeast(5f), rect.bottom),
            height * 0.006f, height * 0.006f, accentPaint
        )
        canvas.save()
        canvas.translate((width - layout.width) / 2f, top + verticalPadding)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawProgress(canvas: Canvas, timeUs: Long) {
        val barHeight = (height * 0.004f).coerceAtLeast(4f)
        val top = height - barHeight
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), trackPaint)
        val fraction = (timeUs.toFloat() / totalDurationUs.toFloat()).coerceIn(0f, 1f)
        canvas.drawRect(0f, top, width * fraction, height.toFloat(), accentPaint)
    }

    private fun parseColor(value: String, fallback: Int): Int =
        runCatching { Color.parseColor(value) }.getOrDefault(fallback)
}
