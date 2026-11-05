package com.sdtech.studio.performance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max

/**
 * Central BitmapPool & LruCache (size = 1/8 of available JVM heap) shared across
 * TemplateRenderer, thumbnails, previews, and decoded image slots.
 *
 * Uses an access-ordered [LinkedHashMap] so LRU eviction works deterministically both on
 * Android devices and in local JVM unit tests without network-dependent test runners.
 *
 * Rule #1 compliance: Never calls Bitmap.recycle() on cached or UI-visible bitmaps.
 */
object CentralBitmapPool {
    private const val MIN_CACHE_KB = 4 * 1024 // 4 MB floor for small test heaps

    private data class Entry(val bitmap: Bitmap?, val sizeKb: Int)

    @Volatile
    private var maxCacheKb: Int = computeOneEighthHeapKb()

    private val entries = LinkedHashMap<String, Entry>(32, 0.75f, true)
    private var totalSizeKb: Int = 0

    fun computeOneEighthHeapKb(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): Int {
        val oneEighthKb = (maxHeapBytes / 1024L / 8L).toInt()
        return max(MIN_CACHE_KB, oneEighthKb)
    }

    /** Reconfigures the cache size (used primarily by unit tests). */
    @Synchronized
    fun configureForMaxHeapBytes(maxHeapBytes: Long) {
        maxCacheKb = computeOneEighthHeapKb(maxHeapBytes)
        evictAll()
    }

    val maxSizeKb: Int
        @Synchronized get() = maxCacheKb

    val currentSizeKb: Int
        @Synchronized get() = totalSizeKb

    val usedSizeKb: Int
        @Synchronized get() = totalSizeKb

    val entryCount: Int
        @Synchronized get() = entries.size

    private fun estimateSizeKb(bitmap: Bitmap?, fallbackKb: Int = 64): Int {
        if (bitmap == null) return fallbackKb.coerceAtLeast(1)
        val bytes = runCatching { bitmap.allocationByteCount }.getOrDefault(0)
        return if (bytes > 0) (bytes / 1024).coerceAtLeast(1) else fallbackKb.coerceAtLeast(1)
    }

    @Synchronized
    fun get(key: String): Bitmap? {
        val entry = entries[key] ?: return null
        val bmp = entry.bitmap ?: return null
        val recycled = runCatching { bmp.isRecycled }.getOrDefault(false)
        if (recycled) {
            entries.remove(key)
            totalSizeKb = (totalSizeKb - entry.sizeKb).coerceAtLeast(0)
            return null
        }
        return bmp
    }

    @Synchronized
    fun hasEntry(key: String): Boolean = entries.containsKey(key)

    @Synchronized
    fun put(key: String, bitmap: Bitmap, estimatedKb: Int = 64) {
        val recycled = runCatching { bitmap.isRecycled }.getOrDefault(false)
        if (recycled) return
        val sizeKb = estimateSizeKb(bitmap, estimatedKb)
        entries.remove(key)?.let { previous ->
            totalSizeKb = (totalSizeKb - previous.sizeKb).coerceAtLeast(0)
        }
        entries[key] = Entry(bitmap, sizeKb)
        totalSizeKb += sizeKb
        trimToSizeKb(maxCacheKb)
    }

    /** Used by JVM unit/stress tests to exercise LRU byte-accounting without native Bitmap allocation. */
    @Synchronized
    fun putTrackedEntryForTest(key: String, sizeKb: Int) {
        val clamped = sizeKb.coerceAtLeast(1)
        entries.remove(key)?.let { previous ->
            totalSizeKb = (totalSizeKb - previous.sizeKb).coerceAtLeast(0)
        }
        entries[key] = Entry(null, clamped)
        totalSizeKb += clamped
        trimToSizeKb(maxCacheKb)
    }

    @Synchronized
    fun getOrPut(key: String, loader: () -> Bitmap?): Bitmap? {
        get(key)?.let { return it }
        val loaded = loader() ?: return null
        put(key, loaded)
        return loaded
    }

    @Synchronized
    private fun trimToSizeKb(targetMaxKb: Int) {
        val limit = targetMaxKb.coerceAtLeast(0)
        val iterator = entries.entries.iterator()
        while (totalSizeKb > limit && iterator.hasNext()) {
            val evicted = iterator.next()
            totalSizeKb = (totalSizeKb - evicted.value.sizeKb).coerceAtLeast(0)
            iterator.remove()
            // Rule #1: Never call Bitmap.recycle() on evicted bitmaps that may still be drawn in UI.
        }
    }

    /** Trims the cache to [fraction] of its current max size (0f = evictAll). */
    @Synchronized
    fun trimToFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped <= 0f) {
            evictAll()
        } else {
            val targetSize = (maxCacheKb * clamped).toInt().coerceAtLeast(0)
            trimToSizeKb(targetSize)
        }
    }

    @Synchronized
    fun evictAll() {
        entries.clear()
        totalSizeKb = 0
    }

    /**
     * Decodes a file bounded by [maxEdgePx] and stores it in the central LruCache.
     */
    fun decodeFileCached(path: String, maxEdgePx: Int = 2048): Bitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        val key = "file|$path|edge=$maxEdgePx|mod=${file.lastModified()}"
        get(key)?.let { return it }
        val decoded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdgePx) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }.getOrNull() ?: return null
        put(key, decoded)
        return decoded
    }
}
