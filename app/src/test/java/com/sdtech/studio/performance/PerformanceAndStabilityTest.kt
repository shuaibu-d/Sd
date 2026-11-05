package com.sdtech.studio.performance

import android.content.ComponentCallbacks2
import android.os.PowerManager
import com.sdtech.studio.CrashLogger
import com.sdtech.studio.render.LogoPlacementMode
import com.sdtech.studio.render.LogoPlacementRules
import com.sdtech.studio.render.RatioProfile
import com.sdtech.studio.render.RenderOptions
import com.sdtech.studio.render.Template
import com.sdtech.studio.render.TemplateLayer
import com.sdtech.studio.render.TemplateSamples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PerformanceAndStabilityTest {

    private lateinit var tempFilesDir: File
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        tempFilesDir = Files.createTempDirectory("sd_studio_perf_test").toFile()
        CentralBitmapPool.configureForMaxHeapBytes(Runtime.getRuntime().maxMemory())
        RenderDispatcher.resetStats()
        MemoryGuard.resetForTesting()
        ThermalGuard.resetForTesting()
        ErrorBoundary.installForTesting(tempFilesDir)
        ErrorBoundary.dismiss()
        CrashLogger.clearDir(tempFilesDir)
    }

    @After
    fun tearDown() {
        CentralBitmapPool.evictAll()
        MemoryGuard.resetForTesting()
        ThermalGuard.resetForTesting()
        ErrorBoundary.dismiss()
        tempFilesDir.deleteRecursively()
    }

    private fun resolveAssetsDir(subPath: String): File {
        val candidates = listOf(
            File("src/main/assets/$subPath"),
            File("app/src/main/assets/$subPath")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate assets directory for $subPath from ${File(".").absolutePath}")
    }

    private fun loadAllPostTemplatesFromAssets(): List<Template> {
        val dir = resolveAssetsDir("templates")
        return dir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file -> json.decodeFromString<Template>(file.readText()) }
    }

    @Test
    fun centralBitmapPool_usesOneEighthHeapAndEvictsLruWithinBudget() {
        val expectedMaxKb = CentralBitmapPool.computeOneEighthHeapKb()
        assertEquals(expectedMaxKb, CentralBitmapPool.maxSizeKb)

        // Reconfigure to 32 MB simulated heap => 1/8 = 4096 KB pool budget
        CentralBitmapPool.configureForMaxHeapBytes(32L * 1024L * 1024L)
        assertEquals(4096, CentralBitmapPool.maxSizeKb)

        CentralBitmapPool.putTrackedEntryForTest("thumb:1", 1500)
        CentralBitmapPool.putTrackedEntryForTest("thumb:2", 1500)
        assertEquals(3000, CentralBitmapPool.usedSizeKb)
        assertTrue(CentralBitmapPool.hasEntry("thumb:1"))
        assertTrue(CentralBitmapPool.hasEntry("thumb:2"))

        // Adding another 1500 KB entry exceeds 4096 KB and must evict LRU ("thumb:1")
        CentralBitmapPool.putTrackedEntryForTest("thumb:3", 1500)
        assertFalse("LRU entry thumb:1 should be evicted when pool exceeds 1/8 heap", CentralBitmapPool.hasEntry("thumb:1"))
        assertTrue(CentralBitmapPool.hasEntry("thumb:2"))
        assertTrue(CentralBitmapPool.hasEntry("thumb:3"))
        assertEquals(3000, CentralBitmapPool.usedSizeKb)

        CentralBitmapPool.trimToFraction(0.5f)
        assertTrue("Trim to 50% must cap usedSizeKb <= 2048", CentralBitmapPool.usedSizeKb <= 2048)

        CentralBitmapPool.evictAll()
        assertEquals(0, CentralBitmapPool.usedSizeKb)
    }

    @Test
    fun renderDispatcher_limitsParallelismToTwoAndCancelsOnScrollOrLeave() = runBlocking {
        RenderDispatcher.resetStats()
        val completed = AtomicInteger(0)

        val jobs = (1..8).map {
            async {
                RenderDispatcher.withRenderContext {
                    Thread.sleep(35)
                    completed.incrementAndGet()
                }
            }
        }
        jobs.awaitAll()

        assertEquals(8, completed.get())
        assertTrue(
            "RenderDispatcher concurrency (${RenderDispatcher.maxObservedConcurrency}) must never exceed 2",
            RenderDispatcher.maxObservedConcurrency in 1..2
        )

        // Simulate user scrolling away / leaving screen before queued renders finish
        val cancelledCount = AtomicInteger(0)
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repeat(6) {
            screenScope.launch {
                try {
                    RenderDispatcher.withRenderContext {
                        delay(500)
                    }
                } catch (e: CancellationException) {
                    cancelledCount.incrementAndGet()
                    throw e
                }
            }
        }
        delay(40)
        screenScope.cancel("User left screen")
        delay(80)
        assertTrue("Cancelling screen scope should cancel in-flight/queued renders", cancelledCount.get() >= 1)
    }

    @Test
    fun memoryGuard_trimsCacheAndLowersPreviewResolutionOnMemoryPressure() {
        CentralBitmapPool.configureForMaxHeapBytes(64L * 1024L * 1024L) // 8192 KB max
        CentralBitmapPool.putTrackedEntryForTest("mem_test_1", 3000)
        CentralBitmapPool.putTrackedEntryForTest("mem_test_2", 3000)
        assertEquals(MemoryPressureLevel.NORMAL, MemoryGuard.pressureLevel.value)
        assertEquals(1.0f, MemoryGuard.pressureLevel.value.previewScale, 0.001f)

        MemoryGuard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(MemoryPressureLevel.MODERATE, MemoryGuard.pressureLevel.value)
        assertEquals(0.75f, MemoryGuard.pressureLevel.value.previewScale, 0.001f)
        assertTrue(CentralBitmapPool.usedSizeKb <= CentralBitmapPool.maxSizeKb / 2)

        MemoryGuard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(MemoryPressureLevel.CRITICAL, MemoryGuard.pressureLevel.value)
        assertEquals(0.5f, MemoryGuard.pressureLevel.value.previewScale, 0.001f)
        assertEquals("Critical trim should evict cached bitmaps", 0, CentralBitmapPool.usedSizeKb)
    }

    @Test
    fun thermalGuard_scalesDownPreviewsAndWarnsBeforeExportWhenHot() {
        assertFalse(ThermalGuard.isDeviceHot)
        assertFalse(ThermalGuard.shouldWarnBeforeExport)
        assertEquals(1.0f, ThermalGuard.thermalState.value.previewScale, 0.001f)

        ThermalGuard.updateFromPowerThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        assertTrue(ThermalGuard.isDeviceHot)
        assertTrue(ThermalGuard.shouldWarnBeforeExport)
        assertEquals(0.65f, ThermalGuard.thermalState.value.previewScale, 0.001f)
        assertTrue(ThermalGuard.exportThermalWarningMessage().contains("warm", ignoreCase = true))

        ThermalGuard.updateFromPowerThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        assertTrue(ThermalGuard.isDeviceHot)
        assertTrue(ThermalGuard.shouldWarnBeforeExport)
        assertEquals(0.5f, ThermalGuard.thermalState.value.previewScale, 0.001f)
        assertTrue(ThermalGuard.effectiveGalleryThumbnailWidthPx < 420)
        assertTrue(ThermalGuard.effectiveEditorMaxEdgePx < 1080)
    }

    @Test
    fun errorBoundary_catchesRenderAndExportExceptionsLogsToCrashLogAndSupportsRetry() = runBlocking {
        val retried = AtomicBoolean(false)

        val result = ErrorBoundary.runGuarded(
            source = "UnitTestRender",
            title = "Preview render failed",
            message = "Tap Retry to render again.",
            onRetry = { retried.set(true) }
        ) {
            throw IllegalStateException("Simulated renderer failure")
        }

        assertNull("Guarded failure must return null instead of crashing", result)
        val currentError = ErrorBoundary.currentError.value
        assertNotNull("ErrorBoundary must publish RecoverableErrorEvent for UI dialog", currentError)
        assertTrue(currentError!!.operationName.contains("Preview render failed"))

        val crashLog = CrashLogger.readLatestFromDir(tempFilesDir).orEmpty()
        assertTrue("Crash log must contain handled exception details", crashLog.contains("Simulated renderer failure"))
        assertTrue("Crash log must record source tag", crashLog.contains("UnitTestRender"))

        ErrorBoundary.retryCurrent()
        assertTrue("Retry action must be invoked when user taps Retry", retried.get())
        assertNull("ErrorBoundary dialog state must clear after retry", ErrorBoundary.currentError.value)
    }

    @Test
    fun performanceBudgetsAndStressTest_coldStartGalleryEditorAnd50IterationsUnderBudget() = runBlocking {
        // 1. Cold Start initialization measurement (Budget: < 2,000 ms)
        val coldStartBeginNs = System.nanoTime()
        CentralBitmapPool.configureForMaxHeapBytes(Runtime.getRuntime().maxMemory())
        MemoryGuard.resetForTesting()
        ThermalGuard.resetForTesting()
        ErrorBoundary.installForTesting(tempFilesDir)
        val templates = loadAllPostTemplatesFromAssets()
        val coldStartMs = (System.nanoTime() - coldStartBeginNs) / 1_000_000L
        assertEquals(12, templates.size)

        // 2. Opening Template Gallery measurement (Budget: < 1,000 ms for loading templates + initial visible thumbnails)
        val galleryStartNs = System.nanoTime()
        val initialGalleryThumbs = templates.take(6).map { template ->
            async {
                RenderDispatcher.withRenderContext {
                    val thumbWidth = ThermalGuard.effectiveGalleryThumbnailWidthPx
                    val profile = RatioProfile.forRatio("1:1")
                    val placement = LogoPlacementRules.forProfile(
                        profile.copy(width = thumbWidth, height = thumbWidth),
                        LogoPlacementMode.SAFE,
                        1f
                    )
                    assertTrue(placement.width > 0f)
                    val estimatedKb = (thumbWidth * thumbWidth * 4) / 1024
                    val cacheKey = "thumb:${template.id}|1:1|dark|$thumbWidth"
                    CentralBitmapPool.putTrackedEntryForTest(cacheKey, estimatedKb)
                    cacheKey
                }
            }
        }.awaitAll()
        val galleryOpenMs = (System.nanoTime() - galleryStartNs) / 1_000_000L
        assertEquals(6, initialGalleryThumbs.size)

        // 3. Opening Post Editor measurement (Budget: < 1,000 ms for loading template + live preview pipeline)
        val editorStartNs = System.nanoTime()
        val firstTemplate = templates.first()
        RenderDispatcher.withRenderContext {
            val maxEdge = ThermalGuard.effectiveEditorMaxEdgePx
            val options = RenderOptions(
                showSafeZones = false,
                logoPlacementMode = LogoPlacementMode.SAFE,
                maxDimension = maxEdge
            )
            assertEquals(maxEdge, options.maxDimension)
            val textLayers = firstTemplate.layers.filterIsInstance<TemplateLayer.Text>()
            assertTrue(textLayers.isNotEmpty())
            val previewKb = (maxEdge * maxEdge * 4) / 1024
            CentralBitmapPool.putTrackedEntryForTest("editor_preview:${firstTemplate.id}", previewKb)
        }
        val editorOpenMs = (System.nanoTime() - editorStartNs) / 1_000_000L

        // 4. Stress test: Open/close editor 50 times across all 12 templates + rapid gallery scroll/cancel + memory trim
        var peakHeapMb = 0L
        val ratios = listOf("1:1", "4:5", "9:16", "16:9")
        for (iteration in 0 until 50) {
            val template = templates[iteration % templates.size]
            val ratio = ratios[iteration % ratios.size]

            // Simulate rapid gallery scroll job that gets cancelled mid-scroll every 5th iteration
            if (iteration % 5 == 0) {
                val scrollJob = launch {
                    RenderDispatcher.withRenderContext {
                        delay(50)
                        CentralBitmapPool.putTrackedEntryForTest("scroll_thumb:$iteration", 512)
                    }
                }
                scrollJob.cancel()
            }

            // Simulate opening editor, resolving layers & sample values, rendering preview, and caching in CentralBitmapPool
            val ok = ErrorBoundary.runGuarded(
                source = "StressEditor:$iteration",
                title = "Stress test error",
                message = "Should not fail"
            ) {
                RenderDispatcher.withRenderContext {
                    val baseProfile = RatioProfile.forRatio(ratio)
                    val maxEdge = ThermalGuard.effectiveEditorMaxEdgePx
                    val scale = maxEdge.toFloat() / maxOf(baseProfile.width, baseProfile.height)
                    val scaledW = (baseProfile.width * scale).toInt().coerceAtLeast(1)
                    val scaledH = (baseProfile.height * scale).toInt().coerceAtLeast(1)
                    val resolvedTexts = template.layers.filterIsInstance<TemplateLayer.Text>().associate { layer ->
                        layer.id to (TemplateSamples.values[layer.id] ?: layer.text)
                    }
                    assertTrue(resolvedTexts.isNotEmpty())
                    val kb = (scaledW * scaledH * 4) / 1024
                    CentralBitmapPool.putTrackedEntryForTest("editor_preview:${template.id}:$ratio", kb)
                    true
                }
            }
            assertEquals(true, ok)

            if (iteration == 25) {
                MemoryGuard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                MemoryGuard.resetForTesting()
            }

            val runtime = Runtime.getRuntime()
            val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
            if (usedHeapMb > peakHeapMb) peakHeapMb = usedHeapMb
        }

        println(
            "SDStudioBenchmarkResults: coldStartMs=$coldStartMs, galleryOpenMs=$galleryOpenMs, " +
                "editorOpenMs=$editorOpenMs, peakHeapMb=$peakHeapMb, poolUsedKb=${CentralBitmapPool.usedSizeKb}/${CentralBitmapPool.maxSizeKb}"
        )

        // Enforce performance budgets from docs/PERFORMANCE.md
        assertTrue("Cold start ($coldStartMs ms) exceeded 2000 ms budget", coldStartMs < 2_000L)
        assertTrue("Gallery open ($galleryOpenMs ms) exceeded 1000 ms budget", galleryOpenMs < 1_000L)
        assertTrue("Editor open ($editorOpenMs ms) exceeded 1000 ms budget", editorOpenMs < 1_000L)
        assertTrue("Peak heap ($peakHeapMb MB) exceeded 400 MB budget", peakHeapMb < 400L)
        assertTrue(
            "CentralBitmapPool usage (${CentralBitmapPool.usedSizeKb} KB) must stay within max (${CentralBitmapPool.maxSizeKb} KB)",
            CentralBitmapPool.usedSizeKb <= CentralBitmapPool.maxSizeKb
        )
        assertTrue(
            "RenderDispatcher concurrency (${RenderDispatcher.maxObservedConcurrency}) must not exceed 2",
            RenderDispatcher.maxObservedConcurrency in 1..2
        )
        assertNull("Stress test must complete with zero unhandled errors", ErrorBoundary.currentError.value)
    }
}
