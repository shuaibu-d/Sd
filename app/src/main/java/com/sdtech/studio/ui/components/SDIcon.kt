package com.sdtech.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SDIconKind {
    Home, Posts, Video, Library, Brand, Settings, Calendar, Back, Plus, Undo, Redo,
    Share, Download, Mic, Star, StarOutline, Check, Close, Edit, Trash, Play, Sparkle, Text, Layers
}

/** Hand-drawn 24-unit icon set (no extra libraries needed). */
@Composable
fun SDIcon(
    kind: SDIconKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    val a11yModifier = if (contentDescription != null) {
        modifier.semantics {
            this.contentDescription = contentDescription
        }
    } else {
        modifier
    }
    Canvas(a11yModifier.size(size)) { drawIcon(kind, tint, this.size.minDimension / 24f) }
}

private fun DrawScope.drawIcon(kind: SDIconKind, c: Color, s: Float) {
    val line = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    fun path(vararg pts: Pair<Float, Float>, closed: Boolean = false): Path = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.first * s, p.second * s) else lineTo(p.first * s, p.second * s) }
        if (closed) close()
    }
    fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, style: androidx.compose.ui.graphics.drawscope.DrawStyle = line) =
        drawRoundRect(c, o(x, y), Size(w * s, h * s), CornerRadius(r * s, r * s), style = style)
    fun ln(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = 2f * s, cap = StrokeCap.Round)
    when (kind) {
        SDIconKind.Home -> {
            drawPath(path(3f to 11f, 12f to 3f, 21f to 11f), c, style = line)
            drawPath(path(5f to 10f, 5f to 20f, 19f to 20f, 19f to 10f), c, style = line)
            drawPath(path(10f to 20f, 10f to 14f, 14f to 14f, 14f to 20f), c, style = line)
        }
        SDIconKind.Posts -> {
            rr(3f, 3f, 18f, 18f, 4f)
            drawCircle(c, 1.8f * s, o(9f, 9f))
            drawPath(path(3f to 17f, 9f to 12f, 13f to 16f, 16f to 13f, 21f to 18f), c, style = line)
        }
        SDIconKind.Video -> {
            rr(2f, 5f, 20f, 14f, 3f)
            drawPath(path(10f to 9f, 10f to 15f, 15f to 12f, closed = true), c, style = Fill)
        }
        SDIconKind.Library -> {
            rr(3f, 3f, 8f, 8f, 2f); rr(13f, 3f, 8f, 8f, 2f); rr(3f, 13f, 8f, 8f, 2f); rr(13f, 13f, 8f, 8f, 2f)
        }
        SDIconKind.Brand -> {
            drawPath(path(12f to 2.5f, 21.5f to 12f, 12f to 21.5f, 2.5f to 12f, closed = true), c, style = line)
            drawCircle(c, 3f * s, o(12f, 12f))
        }
        SDIconKind.Settings -> {
            ln(3f, 6f, 21f, 6f); ln(3f, 12f, 21f, 12f); ln(3f, 18f, 21f, 18f)
            drawCircle(c, 2.4f * s, o(8f, 6f)); drawCircle(c, 2.4f * s, o(16f, 12f)); drawCircle(c, 2.4f * s, o(9f, 18f))
        }
        SDIconKind.Calendar -> {
            rr(3f, 5f, 18f, 16f, 3f); ln(3f, 10f, 21f, 10f); ln(8f, 3f, 8f, 7f); ln(16f, 3f, 16f, 7f)
        }
        SDIconKind.Back -> drawPath(path(15f to 5f, 8f to 12f, 15f to 19f), c, style = line)
        SDIconKind.Plus -> { ln(12f, 5f, 12f, 19f); ln(5f, 12f, 19f, 12f) }
        SDIconKind.Undo -> {
            drawPath(path(9f to 6f, 4f to 11f, 9f to 16f), c, style = line)
            drawPath(Path().apply {
                moveTo(4f * s, 11f * s); lineTo(14f * s, 11f * s)
                quadraticTo(20f * s, 11f * s, 20f * s, 16f * s)
                quadraticTo(20f * s, 20f * s, 14f * s, 20f * s); lineTo(11f * s, 20f * s)
            }, c, style = line)
        }
        SDIconKind.Redo -> {
            drawPath(path(15f to 6f, 20f to 11f, 15f to 16f), c, style = line)
            drawPath(Path().apply {
                moveTo(20f * s, 11f * s); lineTo(10f * s, 11f * s)
                quadraticTo(4f * s, 11f * s, 4f * s, 16f * s)
                quadraticTo(4f * s, 20f * s, 10f * s, 20f * s); lineTo(13f * s, 20f * s)
            }, c, style = line)
        }
        SDIconKind.Share -> {
            drawCircle(c, 2.6f * s, o(6f, 12f)); drawCircle(c, 2.6f * s, o(18f, 6f)); drawCircle(c, 2.6f * s, o(18f, 18f))
            ln(8.2f, 10.8f, 15.8f, 7.2f); ln(8.2f, 13.2f, 15.8f, 16.8f)
        }
        SDIconKind.Download -> {
            ln(12f, 4f, 12f, 15f); drawPath(path(7f to 11f, 12f to 16f, 17f to 11f), c, style = line); ln(5f, 20f, 19f, 20f)
        }
        SDIconKind.Mic -> {
            rr(9f, 3f, 6f, 11f, 3f)
            drawPath(Path().apply {
                moveTo(5f * s, 11f * s); quadraticTo(5f * s, 17f * s, 12f * s, 17f * s); quadraticTo(19f * s, 17f * s, 19f * s, 11f * s)
            }, c, style = line)
            ln(12f, 17f, 12f, 21f)
        }
        SDIconKind.Star, SDIconKind.StarOutline -> {
            val pts = (0 until 10).map { i ->
                val r = if (i % 2 == 0) 9.5f else 4.2f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                (12f + r * Math.cos(a).toFloat()) to (12.5f + r * Math.sin(a).toFloat())
            }
            val p = path(*pts.toTypedArray(), closed = true)
            if (kind == SDIconKind.Star) drawPath(p, c, style = Fill) else drawPath(p, c, style = line)
        }
        SDIconKind.Check -> drawPath(path(5f to 12.5f, 10f to 17.5f, 19f to 7f), c, style = line)
        SDIconKind.Close -> { ln(6f, 6f, 18f, 18f); ln(18f, 6f, 6f, 18f) }
        SDIconKind.Edit -> drawPath(path(4f to 20f, 8f to 19f, 19f to 8f, 16f to 5f, 5f to 16f, closed = true), c, style = line)
        SDIconKind.Trash -> {
            ln(5f, 7f, 19f, 7f); rr(7f, 7f, 10f, 13f, 2f); ln(10f, 4f, 14f, 4f)
        }
        SDIconKind.Play -> drawPath(path(7f to 4f, 7f to 20f, 20f to 12f, closed = true), c, style = Fill)
        SDIconKind.Sparkle -> drawPath(Path().apply {
            moveTo(12f * s, 2f * s); quadraticTo(13f * s, 11f * s, 22f * s, 12f * s)
            quadraticTo(13f * s, 13f * s, 12f * s, 22f * s); quadraticTo(11f * s, 13f * s, 2f * s, 12f * s)
            quadraticTo(11f * s, 11f * s, 12f * s, 2f * s)
        }, c, style = Fill)
        SDIconKind.Text -> { ln(5f, 6f, 19f, 6f); ln(12f, 6f, 12f, 20f); ln(9f, 20f, 15f, 20f) }
        SDIconKind.Layers -> {
            drawPath(path(12f to 3f, 21f to 8f, 12f to 13f, 3f to 8f, closed = true), c, style = line)
            drawPath(path(3f to 13f, 12f to 18f, 21f to 13f), c, style = line)
        }
    }
}
