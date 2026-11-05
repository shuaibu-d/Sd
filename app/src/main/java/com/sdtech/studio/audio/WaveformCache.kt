@file:Suppress("UnsafeOptInUsageError")

package com.sdtech.studio.audio

import android.content.Context
import android.media.MediaFormat
import androidx.media3.inspector.MediaExtractorCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cached, low-overhead waveform sampler and live-recording ring buffer.
 *
 * Avoids per-tick list allocations during recording and caches downsampled waveform
 * envelopes in an access-ordered LRU cache so Compose recompositions never re-read
 * or re-sample audio files.
 */
object WaveformCache {
    const val DEFAULT_BUCKET_COUNT = 64
    private const val MAX_CACHE_ENTRIES = 64
    private val defaultPlaceholder = floatArrayOf(0.08f, 0.16f, 0.12f, 0.20f, 0.10f)

    private val lru = object : LinkedHashMap<String, FloatArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    private fun cacheKey(filePath: String, bucketCount: Int): String {
        val file = File(filePath)
        val stamp = if (file.isFile) "${file.length()}:${file.lastModified()}" else "0:0"
        return "wave|$filePath|$stamp|$bucketCount"
    }

    @Synchronized
    fun get(filePath: String?, bucketCount: Int = DEFAULT_BUCKET_COUNT): FloatArray? {
        if (filePath.isNullOrBlank()) return null
        return lru[cacheKey(filePath, bucketCount)]
    }

    @Synchronized
    fun put(filePath: String, levels: FloatArray, bucketCount: Int = DEFAULT_BUCKET_COUNT): FloatArray {
        val normalized = resampleBuckets(levels, bucketCount)
        lru[cacheKey(filePath, bucketCount)] = normalized
        return normalized
    }

    @Synchronized
    fun put(filePath: String, levels: List<Float>, bucketCount: Int = DEFAULT_BUCKET_COUNT): FloatArray {
        val raw = if (levels.isEmpty()) defaultPlaceholder else FloatArray(levels.size) { levels[it].coerceIn(0.04f, 1f) }
        return put(filePath, raw, bucketCount)
    }

    @Synchronized
    fun clear() {
        lru.clear()
    }

    @Synchronized
    fun size(): Int = lru.size

    /**
     * Returns the cached waveform array immediately if present; otherwise populates from
     * [storedLevels] or samples encoded audio packet energies off the main thread via
     * Media3 [MediaExtractorCompat] and caches the result.
     */
    suspend fun getOrSample(
        context: Context,
        filePath: String?,
        storedLevels: List<Float> = emptyList(),
        bucketCount: Int = DEFAULT_BUCKET_COUNT
    ): FloatArray {
        if (filePath.isNullOrBlank()) {
            return if (storedLevels.isNotEmpty()) {
                resampleBuckets(FloatArray(storedLevels.size) { storedLevels[it] }, bucketCount)
            } else {
                FloatArray(0)
            }
        }
        get(filePath, bucketCount)?.let { return it }
        if (storedLevels.isNotEmpty()) {
            return put(filePath, storedLevels, bucketCount)
        }
        return withContext(Dispatchers.IO) {
            get(filePath, bucketCount)?.let { return@withContext it }
            val file = File(filePath)
            val sampled = if (file.isFile && file.length() > 256L) {
                runCatching { samplePacketEnvelopeWithMedia3(context, file, bucketCount) }.getOrNull()
            } else {
                null
            }
            val resolved = sampled?.takeIf { it.isNotEmpty() } ?: resampleBuckets(defaultPlaceholder, bucketCount)
            put(filePath, resolved, bucketCount)
        }
    }

    private fun samplePacketEnvelopeWithMedia3(context: Context, file: File, bucketCount: Int): FloatArray {
        val extractor = MediaExtractorCompat(context.applicationContext)
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return resampleBuckets(defaultPlaceholder, bucketCount)
            extractor.selectTrack(track)
            val packetValues = FloatArray(512)
            var count = 0
            val scratch = ByteBuffer.allocate(32 * 1024)
            while (count < packetValues.size) {
                scratch.clear()
                val read = extractor.readSampleData(scratch, 0)
                if (read < 0) break
                var energy = 0.0
                val step = max(1, read / 32)
                var samples = 0
                var i = 0
                while (i < read) {
                    val centered = ((scratch.get(i).toInt() and 0xFF) - 128) / 128.0
                    energy += centered * centered
                    samples++
                    i += step
                }
                val rms = if (samples > 0) sqrt(energy / samples).toFloat() else 0.1f
                packetValues[count++] = rms.coerceIn(0.04f, 1f)
                if (!extractor.advance()) break
            }
            if (count == 0) return resampleBuckets(defaultPlaceholder, bucketCount)
            val valid = packetValues.copyOf(count)
            val peak = valid.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
            for (idx in valid.indices) {
                valid[idx] = (valid[idx] / peak).coerceIn(0.06f, 1f)
            }
            return resampleBuckets(valid, bucketCount)
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun resampleBuckets(source: FloatArray, bucketCount: Int = DEFAULT_BUCKET_COUNT): FloatArray {
        val target = bucketCount.coerceAtLeast(1)
        if (source.isEmpty()) return FloatArray(target) { 0.08f }
        if (source.size == target) {
            return FloatArray(target) { source[it].coerceIn(0.04f, 1f) }
        }
        return FloatArray(target) { bucket ->
            val start = (bucket * source.size) / target
            val end = (((bucket + 1) * source.size) / target).coerceAtLeast(start + 1).coerceAtMost(source.size)
            var peak = 0.04f
            for (i in start until end) {
                val v = source[i].coerceIn(0.04f, 1f)
                if (v > peak) peak = v
            }
            peak
        }
    }
}

/**
 * Pre-allocated fixed-capacity ring buffer for live recording waveform samples.
 * Eliminates per-tick list copying (`liveWave + level`) while recording.
 */
class LiveWaveformBuffer(val capacity: Int = WaveformCache.DEFAULT_BUCKET_COUNT) {
    private val ring = FloatArray(capacity)
    private var head = 0
    var count: Int = 0
        private set

    fun reset() {
        head = 0
        count = 0
        ring.fill(0f)
    }

    fun push(level: Float) {
        val clamped = level.coerceIn(0.04f, 1f)
        if (count < capacity) {
            ring[count++] = clamped
        } else {
            ring[head] = clamped
            head = (head + 1) % capacity
        }
    }

    fun getAt(index: Int): Float {
        if (index < 0 || index >= count) return 0.04f
        val actualIndex = if (count < capacity) index else (head + index) % capacity
        return ring[actualIndex]
    }

    fun toFloatArray(): FloatArray {
        if (count == 0) return floatArrayOf(0.08f, 0.16f, 0.12f, 0.20f, 0.10f)
        return FloatArray(count) { getAt(it) }
    }

    fun toList(): List<Float> {
        val arr = toFloatArray()
        return List(arr.size) { arr[it] }
    }
}
