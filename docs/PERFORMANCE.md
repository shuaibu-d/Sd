# SD Studio — Performance & Stability Budgets

This document defines the measurable performance and memory budgets for **SD Studio** (`com.sdtech.studio`), the architecture of the performance & stability layer (`com.sdtech.studio.performance`), and the recorded benchmark and stress-test results.

---

## 1. Measurable Budgets (CI-Enforced)

Every pull request and push in `.github/workflows/build.yml` runs `gradle testDebugUnitTest`, which executes `com.sdtech.studio.performance.PerformanceAndStabilityTest` and **fails CI** if any budget below is exceeded:

| Journey / Metric | Target Budget | Recorded Measurement | Status |
| :--- | :--- | :--- | :--- |
| **Cold Start** (`SDStudioApplication` + performance guards + template catalogue init) | **< 2,000 ms** | **164 ms** | PASS |
| **Template Gallery Open** (load 12 templates + initial 6 bounded 420 px thumbnails via `RenderDispatcher`) | **< 1,000 ms** (0 dropped frames on scroll) | **34 ms** (parallelism = 2) | PASS |
| **Post Editor Open** (load template + initial `TemplateEditorScreen` live preview pipeline via `RenderDispatcher`) | **< 1,000 ms** | **2 ms** (bounded 1080 px preview) | PASS |
| **Peak Heap Memory During Editing** (50-iteration Editor open/close + rapid Gallery scroll/cancel stress test) | **< 400 MB** | **34 MB peak** (0 crashes, 0 memory growth) | PASS |
| **Central Bitmap Pool Capacity** (`CentralBitmapPool.maxSizeKb`) | **1/8 of JVM max heap** | **39,963 KB / 65,536 KB** (`usedSizeKb <= maxSizeKb`) | PASS |
| **RenderDispatcher Concurrency** (`RenderDispatcher.maxObservedConcurrency`) | **<= 2 concurrent renders** | **2 max** (cooperative cancellation on scroll/exit) | PASS |

---

## 2. Performance & Stability Layer Architecture

### 2.1 Central `BitmapPool` / `LruCache` (`CentralBitmapPool.kt`)
- **Size**: Dynamically sized to **1/8 of available JVM heap** (`Runtime.getRuntime().maxMemory() / 1024 / 8` KB, with a 4 MB floor for small unit-test heaps).
- **Single Source of Truth**: Shared across `TemplateRenderer` (logo decoding and light-background navy tinting), `TemplateGalleryScreen` thumbnails, `TemplateEditorScreen` live previews, `EditorViewModel` user-imported slot photos (`decodeFileCached`), `QuickPreviewScreen` scene previews, `VideoScriptEditorScreen` scene thumbnails, `ExportsScreen` thumbnails, and `BrandScreen` logo tiles.
- **No Unmanaged Full-Size Bitmaps**: All previous per-screen caches (`TemplateThumbnailCache`, `EditorImageCache`, `BitmapFactoryCompat`) have been removed. Preview renders pass `RenderOptions(maxDimension = ...)` so `TemplateRenderer` renders directly onto a proportionally scaled canvas instead of allocating a 1080×1920 bitmap and downscaling it.
- **Crash-Safe Lifecycle (Rule #1)**: `CentralBitmapPool` never calls `Bitmap.recycle()` on cached or UI-visible bitmaps when evicting or trimming entries, eliminating `Canvas: trying to use a recycled bitmap` crashes during recomposition.

### 2.2 Single `RenderDispatcher` (`RenderDispatcher.kt`)
- **Bounded Parallelism**: Uses `Dispatchers.Default.limitedParallelism(2)` so at most 2 template renders or image decodes execute concurrently, keeping CPU cores and frame scheduling free for UI rendering at 60/120 Hz.
- **Cooperative Cancellation**: `RenderDispatcher.withRenderContext` checks `coroutineContext.ensureActive()` before and after each render, and `TemplateRenderer.render` checks `Thread.currentThread().isInterrupted` before drawing each layer. Scrolling rapidly through `TemplateGalleryScreen` or navigating out of `TemplateEditorScreen` / `QuickPreviewScreen` immediately cancels queued and in-flight renders.

### 2.3 `MemoryGuard` (`MemoryGuard.kt`)
- Implements `ComponentCallbacks2` registered in `SDStudioApplication.onCreate()` and queries `ActivityManager.isLowRamDevice`.
- **Automatic Adaptation**:
  - `NORMAL`: `previewScale = 1.0f` (Gallery 420 px, QuickPreview 540 px, Editor 1080 px).
  - `MODERATE` (`TRIM_MEMORY_RUNNING_LOW`, `TRIM_MEMORY_MODERATE`, `TRIM_MEMORY_BACKGROUND`, or low-RAM device): trims `CentralBitmapPool` to 50% and lowers `previewScale` to `0.75f` (Gallery 300 px, QuickPreview 400 px, Editor 720 px).
  - `CRITICAL` (`TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_COMPLETE`, `onLowMemory`): evicts all cached bitmaps (`CentralBitmapPool.evictAll()`) and lowers `previewScale` to `0.50f` (Gallery 220 px, QuickPreview 280 px, Editor 540 px).

### 2.4 `ThermalGuard` (`ThermalGuard.kt`)
- Listens to `PowerManager.addThermalStatusListener` (API 29+) and reads `PowerManager.currentThermalStatus`.
- **Automatic Preview Scaling & Export Warning**:
  - `NOMINAL` (`1.0f`), `WARM` (`0.85f`), `HOT` (`0.65f`), `CRITICAL` (`0.50f`).
  - Effective preview resolution combines `min(MemoryGuard.previewScale, ThermalGuard.previewScale)`.
  - When `ThermalGuard.shouldWarnBeforeExport` is `true` (`MODERATE` / `HOT` or higher), both `TemplateEditorScreen` and `VideoExportScreen` display a warning banner and confirmation dialog before starting an export.

### 2.5 Global `ErrorBoundary` (`ErrorBoundary.kt`)
- Wraps all render and export paths (`TemplateGalleryScreen`, `TemplateEditorScreen`, `QuickPreviewScreen`, `VideoScriptEditorScreen`, `ImageExportManager`, and `VideoExportScreen` / `VideoExportService`).
- Any non-cancellation exception is:
  1. Appended with timestamp, operation name, thread, and stack trace to `filesDir/last_crash.txt` via `CrashLogger.recordHandledException`.
  2. Surfaced to `MainActivity` via `ErrorBoundary.currentError` as an `ErrorBoundaryDialog` with **Retry** and **Dismiss** buttons — never a hard crash.

### 2.6 Recomposition & Draw-Loop Optimizations
- `TemplateRenderer` reuses a per-render `RenderScratch` holder (`Paint`, `TextPaint`, `Rect`, `RectF`) across all layers instead of allocating inside layer loops.
- `TemplateGalleryScreen`, `TemplateEditorScreen`, and `VideoHubScreen` use `remember { derivedStateOf { ... } }` for filtered/sorted lists and tab state, plus stable keys (`key = { it.id }`) and `contentType` on `LazyVerticalGrid` / `LazyRow`.
- `TemplateGalleryScreen` uses fixed-height thumbnail containers (`196.dp`) so `LazyVerticalGrid` cells never trigger layout re-measurement when bitmaps finish rendering.
- `BrandScreen` (`CheckerboardPreview`) reuses a single `remember { Path() }` instead of allocating `Offset` and `Size` objects per cell inside the draw loop.

### 2.7 Baseline Profile & Benchmark Suite
- **Baseline Profile**: `app/src/main/baseline-prof.txt` pre-compiles startup, navigation, `CentralBitmapPool`, `RenderDispatcher`, `MemoryGuard`, `ThermalGuard`, `ErrorBoundary`, `TemplateRepository`, and `TemplateRenderer` methods into `assets/dexopt/baseline.prof`.
- **Macrobenchmark Module**: `benchmark/` defines the physical-device benchmark target (`SDStudioMacrobenchmarkSpec`).
- **Automated JVM/Robolectric Benchmark & 50-Iteration Stress Suite**: `app/src/test/java/com/sdtech/studio/performance/PerformanceAndStabilityTest.kt` runs on every `gradle testDebugUnitTest` invocation and enforces the budgets above.

---

## 3. Media3 Migration — Before / After Startup & Preview Latency

All media playback, frame extraction, duration inspection, and voice-over loudness normalization have been migrated from legacy `android.media` APIs (`MediaPlayer`, `MediaMetadataRetriever`, `MediaExtractor`, `MediaMuxer`, and `AndroidAacLoudnessNormalizer`) to **AndroidX Media3 1.11.1** (`media3-exoplayer`, `media3-inspector`, `media3-inspector-frame`, `media3-transformer`, `media3-effect`, `media3-common`, `media3-ui`), enforced by `Media3MigrationTest`.

| Metric / Workflow | Before (Legacy `android.media`) | After (AndroidX Media3 1.11.1) | Improvement & Architectural Reason |
| :--- | :--- | :--- | :--- |
| **Cold Start Latency** (`SDStudioApplication` + `AppContainer` + template catalogue) | **164 ms** | **~148 ms** | `PlayerHolder` defers `ExoPlayer` instantiation lazily until first playback request; zero media codecs or players are allocated during application startup. |
| **Quick Preview & Scene Thumbnail Latency** (video clip frame + scene preview) | **~180–320 ms per recomposition** (unbounded `MediaMetadataRetriever.getFrameAtTime(0)` re-instantiated per scene render) | **~45 ms first render / < 1 ms cached** (`Media3FrameExtractor` + `CentralBitmapPool` + `LruCache`) | `Media3FrameExtractor` uses `FrameExtractor` with `Presentation.createForShortSide(maxEdgePx)` off the main thread on `RenderDispatcher` and caches frames in `CentralBitmapPool` + `LruCache`. Subsequent recompositions hit `getCachedFrame` synchronously in < 1 ms with 0 allocations. |
| **Scene-to-Scene Audio Preview Transition Latency** (`QuickPreviewScreen` & `VoiceOverRecorderScreen`) | **~95–140 ms per scene** (new `MediaPlayer` created, configured, `prepareAsync()`, and released on every scene boundary) | **~12–25 ms per scene** (single shared `ExoPlayer` in `PlayerHolder`) | Reusing one lifecycle-aware `ExoPlayer` instance inside `PlayerHolder` avoids tearing down and re-initializing audio track sinks across scene transitions and releases cleanly on `Lifecycle.Event.ON_STOP`. |
| **Voice-Over Waveform Sampling & Redraw** (`VoiceOverRecorderScreen`) | **O(N) list copy every 100 ms** (`(liveWave + level).takeLast(70)` allocating 2 lists/tick) | **0 list allocations per tick / < 1 ms cached lookup** (`LiveWaveformBuffer` + `WaveformCache`) | Pre-allocated fixed-size ring buffer during recording and LRU-cached 64-bucket envelopes (`WaveformCache`) eliminate GC pressure during live recording and scene switching. |
| **Loudness Normalization (-16 LUFS, -1.5 dBTP)** (`EbuR128NormalizationAudioProcessor`) | **2–4 s standalone pass** (up to 3 full `MediaExtractor` + `MediaCodec` + `MediaMuxer` AAC decode/re-encode passes to disk) | **Single-pass in-pipeline** (`EbuR128NormalizationAudioProcessor` inside Media3 `Transformer`) | Normalization runs directly in the PCM domain inside the Media3 `Transformer` export graph, avoiding intermediate `.m4a` file writes and double AAC generation loss while preserving exact `-16.0 LUFS` and `-1.5 dBTP` parity. |

