# SD Studio — Progress

## Task 1: Project skeleton
- [x] Single-module Android project (`:app`) using Kotlin DSL and version catalog (`gradle/libs.versions.toml`).
- [x] SD Technologies brand colors, typography, and slogan (*"Create Without Limits."*).
- [x] Application-level uncaught-exception logger (`last_crash.txt`) and next-launch crash recovery dialog.

## Task 2: Visual foundation & 5-tab navigation
- [x] Material 3 dark (default) and light brand color schemes (`MidnightNavy`, `InnovationTeal`, `PremiumGold`, `CloudWhite`, `SlateGrey`).
- [x] Bundled Space Grotesk, Inter, and JetBrains Mono static TTF fonts and OFL licenses.
- [x] 5-tab bottom navigation (`Home`, `Posts`, `Videos`, `Library`, `Brand`) plus detail routes for Post Editor, Caption Editor, Video Script Editor, Voice-Over Recorder, Quick Preview, Video Export, Planner, and Settings.

## Task 3: Local data layer (Room v2 & DataStore)
- [x] Room database (`sd_studio.db`, version 2 with explicit non-destructive `MIGRATION_1_2`) and entities/DAOs for `BrandKit`, `LogoAsset`, `Project`, `ExportRecord`, `CaptionTemplate`, `HashtagGroup`, and `PlannerItem`.
- [x] Non-destructive project upsert (`ProjectDao.save`) preserving `ExportRecord` foreign keys.
- [x] Transactional `FirstLaunchSeeder` and `LogoSeeder` for official SD Technologies brand kit, 4 transparent logo variants, 5 caption templates, and 4 division hashtag groups.

## Task 4: Brand Kit & logo preparation
- [x] `BrandScreen` with Android Photo Picker logo import, corner-sampled flood-fill background removal, checkerboard preview, auto-crop, and monogram crop.
- [x] Official palette editor with Brand Lock enforcement, contact line/CTA editor, caption/hashtag CRUD, and ZIP backup/restore (`BrandBackupManager`).

## Task 5: Template engine, Post Gallery & Post Editor
- [x] Deterministic density-independent `TemplateRenderer` and `LogoPlacementRules` (`SAFE` / `TIGHT`) across `1:1`, `4:5`, `9:16`, and `16:9`.
- [x] 12 bundled post templates (`assets/templates/`) and 3 video scene templates (`assets/video_scenes/`).
- [x] `TemplateGalleryScreen` with LRU thumbnail cache, ratio/theme/category filters, and favourites.
- [x] `TemplateEditorScreen` with `EditorViewModel`, up to 10 carousel slides, undo/redo, auto-save, and multi-ratio PNG/JPEG export (`ImageExportManager`).

## Task 6: Explainer Video Editor, Voice-Over & Media3 Export
- [x] `VideoScriptEditorScreen` with 18-word sentence/paragraph script splitter, scene drag-reorder, and scene text/style/background editing.
- [x] `VoiceOverRecorderScreen` with 3-second countdown, live waveform, and ITU-R BS.1770 / EBU R128 loudness normalization (`-16 LUFS` target, `-1.0 dBTP` verified ceiling).
- [x] `QuickPreviewScreen` with 540 px scene previews, voice-over timeline playback, and scrubbing.
- [x] `VideoExportPipeline` and `VideoExportService` using AndroidX Media3 `1.11.1` `Transformer`, `BitmapOverlay` logo placement, `ExportPolishOverlay` (`CanvasOverlay`) burned-in captions and progress bar, and scene-ducked background music.

## Task 7: Planner, Settings & Hardening
- [x] `PlannerScreen` with month/list views, 5-point posting checklist, and `AlarmManager` local notifications.
- [x] `SettingsScreen` with theme mode, default ratio, default logo placement, storage breakdown, Clear Cache (locked during active export), and Crash Log viewer.
- [x] Removed UI bitmap `recycle()` calls that caused Compose drawing crashes.

## Task 8: Repository Audit & Stabilisation (3 November 2026)
- [x] Ran `gradle testDebugUnitTest assembleDebug` — all unit tests pass and debug APK builds cleanly.
- [x] Eliminated all Kotlin compiler warnings across `AndroidAacLoudnessNormalizer.kt`, `ImageExportManager.kt`, `ExportPolishOverlay.kt`, `VideoExportPipeline.kt`, `VideoExportService.kt`, `PlannerScreen.kt`, and `VoiceOverRecorderScreen.kt`.
- [x] Generated the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) for reproducible builds.
- [x] Created `docs/AUDIT.md` documenting all 13 routes in `AppNavigation.kt`, reachability, dead code, UI-vs-export gaps (e.g., selectable `Fade` transition in `VideoScriptEditorScreen` that is not exported), and the complete font weight audit.
- [x] Removed dead code `PlaceholderScreens.kt` and moved `TemplateDebugScreen.kt` to `app/src/debug/java/com/sdtech/studio/ui/screens/TemplateDebugScreen.kt` so it never ships in release builds.
- [x] Consolidated all root `*_README.md` and `*_NOTES.md` files into `/docs` with `docs/INDEX.md`, keeping `PRIVACY_POLICY.md` and `PLAY_STORE_LISTING.md` at the root.
- [x] Enabled `StrictMode` (thread and VM policy checks) in debug builds (`SDStudioApplication`), enabled R8 minification + resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`) with explicit Room/Serialization keep rules in `app/proguard-rules.pro`, and generated `app/lint-baseline.xml`.

## Task 9: Performance & Stability Layer (4 November 2026)
- [x] Created `CentralBitmapPool` (`com.sdtech.studio.performance.CentralBitmapPool`, size = 1/8 of JVM max heap) shared across `TemplateRenderer`, `TemplateGalleryScreen`, `TemplateEditorScreen`, `EditorViewModel`, `QuickPreviewScreen`, `VideoScriptEditorScreen`, `ExportsScreen`, and `BrandScreen`; removed all separate bitmap caches (`TemplateThumbnailCache`, `EditorImageCache`, `BitmapFactoryCompat`) and added `RenderOptions.maxDimension` so previews render directly at bounded resolution without allocating full-size bitmaps or calling `Bitmap.recycle()` on UI bitmaps.
- [x] Created `RenderDispatcher` (`Dispatchers.Default.limitedParallelism(2)`) with cooperative cancellation (`withRenderContext` + layer interruption checks in `TemplateRenderer`) so scrolling or leaving a screen cancels pending renders.
- [x] Created `MemoryGuard` (`ComponentCallbacks2` + `ActivityManager.isLowRamDevice`) to automatically trim/clear `CentralBitmapPool` and lower preview resolution on memory pressure.
- [x] Created `ThermalGuard` (`PowerManager` thermal listener) to lower preview quality when the device is warm/hot and display a confirmation warning before starting post or video exports.
- [x] Created global `ErrorBoundary` + `ErrorBoundaryDialog` in `MainActivity` that catches render and export exceptions, writes stack traces to `last_crash.txt` via `CrashLogger.recordHandledException`, and offers a friendly Retry dialog.
- [x] Added Baseline Profile (`app/src/main/baseline-prof.txt`), Macrobenchmark spec (`benchmark/`), and automated unit/stress benchmark suite (`PerformanceAndStabilityTest.kt`).
- [x] Fixed recomposition and draw-loop allocations (`derivedStateOf`, stable keys, fixed-height `LazyVerticalGrid` thumbnail containers, reusable `RenderScratch` paints/rects in `TemplateRenderer`, and cached `Path` in `BrandScreen.CheckerboardPreview`).
- [x] Recorded measurable budgets and benchmark numbers in `docs/PERFORMANCE.md` and configured `.github/workflows/build.yml` to fail CI if exceeded.

## Task 10: Full-Screen Video Editor Workspace & Studio UI Redesign (4 November 2026)
- [x] Replaced the long-form `VideoScriptEditorScreen.kt` with a full-screen multi-track video editor workspace (`VideoScriptEditorScreen`, `VideoTimelineTrackView`, `VideoStudioInspectorAndSheets`, `VideoEditorWorkspaceState`) while keeping `ScriptSceneSplitter` intact in `VideoProjectModels.kt`.
- [x] Implemented adaptive layouts: portrait phone layout (top bar, ~45% height canvas preview, draggable canvas/timeline split handle, transport bar, 7-track timeline, and bottom context toolbar with non-covering bottom sheets) and landscape/tablet layout (canvas left, docked inspector right, full-width timeline across the bottom).
- [x] Added Immersive Mode toggle hiding system bars and top/bottom chrome, pinch-to-zoom and two-finger pan on the timeline, magnetic snapping with haptic feedback, 48 dp minimum touch targets with accessibility labels, tap-to-select, long-press multi-select, and draggable left/right trim handles wired to `TimelineProject` and `CommandHistory`.
- [x] Redesigned `HomeScreen`, `TemplateGalleryScreen`, `VideoHubScreen`, `AppNavigation`, and `BrandScaffold` with a cohesive professional studio aesthetic and enhanced `TemplateRenderer` with bundled SD logo fallback and styled photo-slot placeholders.
- [x] Added `VideoEditorWorkspaceTest` verifying selection, multi-selection, trim handles, undo/redo, and saving phone portrait, phone landscape, and 10-inch tablet screenshots in `docs/screens/`.

