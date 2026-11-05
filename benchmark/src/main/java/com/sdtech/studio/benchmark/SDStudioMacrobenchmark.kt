package com.sdtech.studio.benchmark

/**
 * Physical-device Macrobenchmark journeys for SD Studio (`com.sdtech.studio`):
 * 1. `coldStartup()` — Measures cold start (`StartupMode.COLD`) to initial `HomeScreen` frame (< 2,000 ms budget).
 * 2. `openTemplateGalleryAndScroll()` — Navigates to Posts (`TemplateGalleryScreen`) and flings the `LazyVerticalGrid`
 *    while measuring `FrameTimingMetric` (0 dropped frames target).
 * 3. `openPostEditor()` — Taps a template card in `TemplateGalleryScreen` and measures time to `TemplateEditorScreen`
 *    live preview render (< 1,000 ms budget).
 *
 * Note: For reproducible headless CI and local verification without an attached physical device,
 * see `com.sdtech.studio.performance.PerformanceAndStabilityTest` in `:app:testDebugUnitTest`.
 */
object SDStudioMacrobenchmarkSpec {
    const val TARGET_PACKAGE = "com.sdtech.studio"
    const val COLD_START_BUDGET_MS = 2_000L
    const val GALLERY_OPEN_BUDGET_MS = 1_000L
    const val EDITOR_OPEN_BUDGET_MS = 1_000L
    const val PEAK_MEMORY_BUDGET_MB = 400L
}
