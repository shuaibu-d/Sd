@file:Suppress("UnsafeOptInUsageError")

package com.sdtech.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.inspector.MetadataRetriever
import androidx.media3.inspector.frame.FrameExtractor
import com.sdtech.studio.performance.CentralBitmapPool
import com.sdtech.studio.performance.RenderDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Media3-powered, off-main-thread video frame/thumbnail extractor and duration inspector
 * backed by [CentralBitmapPool] and an access-ordered LRU cache.
 *
 * Guarantees:
 * - Zero main-thread or per-recomposition frame extraction.
 * - Uses AndroidX Media3 [FrameExtractor] for thumbnails/frames and [MetadataRetriever]
 *   for media duration inspection.
 */
object Media3FrameExtractor {

    private const val MAX_LOCAL_FRAME_ENTRIES = 64
    private const val MAX_DURATION_ENTRIES = 128

    private val frameLruCache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_LOCAL_FRAME_ENTRIES
        }
    }

    private val durationLruCache = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_DURATION_ENTRIES
        }
    }

    fun frameCacheKey(uriString: String, positionMs: Long = 0L, maxEdgePx: Int = 540): String {
        return "media3_frame|$uriString|pos=$positionMs|edge=$maxEdgePx"
    }

    /**
     * Synchronously returns a cached frame if already available in [CentralBitmapPool] or
     * [frameLruCache] without performing any I/O or decoding. Safe to call inside `remember(key)`.
     */
    fun getCachedFrame(uriString: String?, positionMs: Long = 0L, maxEdgePx: Int = 540): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val key = frameCacheKey(uriString, positionMs, maxEdgePx)
        CentralBitmapPool.get(key)?.let { return it }
        val local = synchronized(frameLruCache) { frameLruCache[key] }
        if (local != null && !runCatching { local.isRecycled }.getOrDefault(false)) {
            CentralBitmapPool.put(key, local)
            return local
        }
        return null
    }

    /**
     * Extracts a video frame or representative thumbnail off the main thread using Media3
     * [FrameExtractor] and caches the downscaled [Bitmap] in both [CentralBitmapPool] and
     * [frameLruCache].
     */
    suspend fun extractFrameCached(
        context: Context,
        uri: Uri,
        positionMs: Long = 0L,
        maxEdgePx: Int = 540
    ): Bitmap? {
        val uriString = uri.toString()
        getCachedFrame(uriString, positionMs, maxEdgePx)?.let { return it }
        val key = frameCacheKey(uriString, positionMs, maxEdgePx)

        return RenderDispatcher.withRenderContext {
            getCachedFrame(uriString, positionMs, maxEdgePx)?.let { return@withRenderContext it }
            val extracted = withContext(Dispatchers.IO) {
                runCatching {
                    val mediaItem = MediaItem.fromUri(uri)
                    val builder = FrameExtractor.Builder(context.applicationContext, mediaItem)
                    if (maxEdgePx > 0) {
                        builder.setEffects(listOf(Presentation.createForShortSide(maxEdgePx)))
                    }
                    builder.build().use { extractor ->
                        val frameFuture = if (positionMs <= 0L) {
                            extractor.thumbnail
                        } else {
                            extractor.getFrame(positionMs)
                        }
                        val frame = frameFuture.get(8, TimeUnit.SECONDS)
                        downscaleIfNeeded(frame.bitmap, maxEdgePx)
                    }
                }.getOrNull()
            } ?: return@withRenderContext null

            synchronized(frameLruCache) {
                frameLruCache[key] = extracted
            }
            CentralBitmapPool.put(key, extracted)
            extracted
        }
    }

    /**
     * Blocking off-main-thread media duration reader using Media3 [MetadataRetriever]
     * with an LRU cache to avoid redundant container parsing.
     */
    fun readDurationMsBlocking(context: Context, uri: Uri): Long {
        val key = uri.toString()
        synchronized(durationLruCache) {
            durationLruCache[key]?.let { if (it > 0L) return it }
        }
        val mediaItem = MediaItem.fromUri(uri)
        val durationMs = MetadataRetriever.Builder(context.applicationContext, mediaItem)
            .build()
            .use { retriever ->
                val durationUs = retriever.retrieveDurationUs().get(10, TimeUnit.SECONDS)
                if (durationUs == C.TIME_UNSET || durationUs <= 0L) 0L else durationUs / 1_000L
            }
        if (durationMs > 0L) {
            synchronized(durationLruCache) {
                durationLruCache[key] = durationMs
            }
        }
        return durationMs
    }

    suspend fun readDurationMs(context: Context, uri: Uri): Long = withContext(Dispatchers.IO) {
        readDurationMsBlocking(context, uri)
    }

    fun clearCaches() {
        synchronized(frameLruCache) { frameLruCache.clear() }
        synchronized(durationLruCache) { durationLruCache.clear() }
    }

    private fun downscaleIfNeeded(source: Bitmap, maxEdgePx: Int): Bitmap {
        if (maxEdgePx <= 0) return source
        val longest = max(source.width, source.height)
        if (longest <= maxEdgePx) return source
        val scale = maxEdgePx.toFloat() / longest.toFloat()
        val targetW = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}
