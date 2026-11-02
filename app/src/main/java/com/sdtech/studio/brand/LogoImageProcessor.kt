package com.sdtech.studio.brand

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.content.ContentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** All decode and pixel work is dispatched to IO; source images are capped at 2048px. */
object LogoImageProcessor {
    const val MAX_EDGE = 2048

    suspend fun decode(resolver: ContentResolver, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image could not be read." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unable to open the selected image.")
        if (max(decoded.width, decoded.height) <= MAX_EDGE) decoded else {
            val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        }
    }

    fun removeEdgeBackground(source: Bitmap, tolerance: Int): Bitmap {
        val w = source.width; val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val corners = intArrayOf(pixels[0], pixels[w - 1], pixels[(h - 1) * w], pixels[h * w - 1])
        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0; var tail = 0
        fun enqueue(index: Int) { if (!visited[index]) { visited[index] = true; queue[tail++] = index } }
        for (x in 0 until w) { enqueue(x); enqueue((h - 1) * w + x) }
        for (y in 0 until h) { enqueue(y * w); enqueue(y * w + w - 1) }
        val limit = tolerance * 4.4f
        while (head < tail) {
            val index = queue[head++]
            val color = pixels[index]
            val closeToCorner = corners.any { corner ->
                val dr = ((color shr 16) and 255) - ((corner shr 16) and 255)
                val dg = ((color shr 8) and 255) - ((corner shr 8) and 255)
                val db = (color and 255) - (corner and 255)
                sqrt((dr * dr + dg * dg + db * db).toFloat()) <= limit
            }
            if (!closeToCorner) continue
            pixels[index] = color and 0x00FFFFFF
            val x = index % w; val y = index / w
            if (x > 0) enqueue(index - 1)
            if (x < w - 1) enqueue(index + 1)
            if (y > 0) enqueue(index - w)
            if (y < h - 1) enqueue(index + w)
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    fun cropToVisible(source: Bitmap, paddingFraction: Float = 0.04f): Bitmap {
        val w = source.width; val h = source.height
        val pixels = IntArray(w * h); source.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w; var top = h; var right = -1; var bottom = -1
        pixels.forEachIndexed { i, color -> if ((color ushr 24) > 8) {
            val x = i % w; val y = i / w
            left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
        } }
        if (right < left || bottom < top) return source
        val pad = (max(right - left + 1, bottom - top + 1) * paddingFraction).toInt()
        val rect = Rect((left - pad).coerceAtLeast(0), (top - pad).coerceAtLeast(0), (right + pad + 1).coerceAtMost(w), (bottom + pad + 1).coerceAtMost(h))
        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
    }

    fun crop(source: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (left * source.width).toInt().coerceIn(0, source.width - 2)
        val t = (top * source.height).toInt().coerceIn(0, source.height - 2)
        val r = (right * source.width).toInt().coerceIn(l + 1, source.width)
        val b = (bottom * source.height).toInt().coerceIn(t + 1, source.height)
        return Bitmap.createBitmap(source, l, t, r - l, b - t)
    }

    suspend fun savePng(contextDir: File, bitmap: Bitmap, name: String): File = withContext(Dispatchers.IO) {
        val folder = File(contextDir, "brand/logos").apply { mkdirs() }
        val target = File(folder, "$name.png")
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        target
    }
}
