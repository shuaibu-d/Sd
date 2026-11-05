package com.sdtech.studio.media

import com.sdtech.studio.audio.LiveWaveformBuffer
import com.sdtech.studio.audio.WaveformCache
import com.sdtech.studio.performance.CentralBitmapPool
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class Media3MigrationTest {

    @Before
    fun setUp() {
        WaveformCache.clear()
        Media3FrameExtractor.clearCaches()
    }

    @After
    fun tearDown() {
        WaveformCache.clear()
        Media3FrameExtractor.clearCaches()
    }

    private fun resolveSourceDirectories(): List<File> {
        val candidates = listOf(
            listOf(File("src/main/java"), File("src/debug/java")),
            listOf(File("app/src/main/java"), File("app/src/debug/java"))
        )
        return candidates.firstOrNull { pair -> pair.first().isDirectory }
            ?: error("Could not locate app source directories from ${File(".").absolutePath}")
    }

    @Test
    fun legacyMediaApis_areNeverImportedOrUsedAnywhereInAppCode() {
        val sourceDirs = resolveSourceDirectories()
        val forbiddenImports = listOf(
            "android.media.MediaPlayer",
            "android.media.MediaMetadataRetriever",
            "android.media.MediaExtractor",
            "android.media.MediaMuxer"
        )
        val forbiddenUsages = listOf(
            Regex("""\bMediaPlayer\b"""),
            Regex("""\bMediaMetadataRetriever\b"""),
            Regex("""\bMediaExtractor\b"""),
            Regex("""\bMediaMuxer\b""")
        )

        val violations = mutableListOf<String>()
        var scannedFileCount = 0

        for (dir in sourceDirs.filter { it.isDirectory }) {
            dir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    scannedFileCount++
                    file.readLines().forEachIndexed { index, rawLine ->
                        val lineNum = index + 1
                        val line = rawLine.substringBefore("//").trim()
                        if (line.isEmpty()) return@forEachIndexed

                        for (forbidden in forbiddenImports) {
                            if (line.contains(forbidden)) {
                                violations += "${file.path}:$lineNum imports/references forbidden legacy API '$forbidden': $rawLine"
                            }
                        }
                        if (line.startsWith("import android.media.*")) {
                            violations += "${file.path}:$lineNum uses wildcard import 'android.media.*'"
                        }
                        for (regex in forbiddenUsages) {
                            if (regex.containsMatchIn(line)) {
                                violations += "${file.path}:$lineNum uses forbidden legacy class '${regex.pattern}': $rawLine"
                            }
                        }
                    }
                }
        }

        assertTrue("Expected to scan app source files", scannedFileCount > 20)
        assertTrue(
            "Legacy Android media APIs (MediaPlayer, MediaMetadataRetriever, MediaExtractor, MediaMuxer) must not be imported or used in app code:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun legacyAndroidAacLoudnessNormalizer_hasBeenRemoved() {
        val sourceDirs = resolveSourceDirectories()
        val legacyFileExists = sourceDirs.any { dir ->
            File(dir, "com/sdtech/studio/audio/AndroidAacLoudnessNormalizer.kt").exists()
        }
        assertFalse(
            "AndroidAacLoudnessNormalizer.kt must be deleted after Media3 AudioProcessor parity is verified",
            legacyFileExists
        )
    }

    @Test
    fun waveformCache_andLiveWaveformBuffer_cacheAndSampleWithoutChurn() {
        val live = LiveWaveformBuffer(capacity = 8)
        for (i in 1..12) {
            live.push(i / 12f)
        }
        assertEquals(8, live.count)
        val snapshot = live.toFloatArray()
        assertEquals(8, snapshot.size)
        assertEquals(5 / 12f, snapshot[0], 1e-4f)
        assertEquals(1.0f, snapshot[7], 1e-4f)

        val fakePath = "/tmp/voice_scene_1.m4a"
        val cached = WaveformCache.put(fakePath, snapshot, bucketCount = 16)
        assertEquals(16, cached.size)
        val hit = WaveformCache.get(fakePath, bucketCount = 16)
        assertNotNull("WaveformCache must return cached waveform on hit", hit)
        assertEquals(16, hit!!.size)
    }

    @Test
    fun media3FrameExtractor_usesDeterministicCacheKeysAndCentralBitmapPool() {
        CentralBitmapPool.configureForMaxHeapBytes(64L * 1024L * 1024L)
        val key = Media3FrameExtractor.frameCacheKey("content://media/external/video/media/42", positionMs = 1500L, maxEdgePx = 540)
        assertEquals("media3_frame|content://media/external/video/media/42|pos=1500|edge=540", key)
        CentralBitmapPool.putTrackedEntryForTest(key, 512)
        assertTrue(CentralBitmapPool.hasEntry(key))
    }

    @Test
    fun playerHolder_releasesSharedPlayerOnLifecycleStop() {
        val fakeLifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        val holder = PlayerHolder(
            context = object : android.content.ContextWrapper(null) {
                override fun getApplicationContext(): android.content.Context = this
                override fun getSystemService(name: String): Any? = null
            }
        )
        fakeLifecycleOwner.lifecycle.addObserver(holder)
        fakeLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        fakeLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        assertEquals(0, holder.activePlayerCount)
        assertFalse(holder.isPlaying.value)
    }
}
