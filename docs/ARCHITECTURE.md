# SD Studio — Architecture

## Project baseline

- **Application ID / namespace:** `com.sdtech.studio`
- **Module:** `:app` only
- **Language:** Kotlin (JVM target 17)
- **UI:** Jetpack Compose + Material 3
- **SDK levels:** compileSdk 36, targetSdk 36, minSdk 26
- **Build:** Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), Gradle wrapper
- **Navigation:** Navigation Compose `2.8.9`
- **Brand:** SD Studio by SD Technologies — *"Create Without Limits."*
- **Palette:** Midnight Navy (`#101C2C`), Innovation Teal (`#16A6A0`), Premium Gold (`#D9B45B`), Cloud White (`#F3F5F8`), and Slate Grey (`#263445`)

## Current structure

```text
app/
  src/main/
    AndroidManifest.xml
    assets/
      brand/                # Official SD Technologies logo assets
      font-licenses/        # OFL licence texts for bundled fonts
      templates/            # 12 social post JSON templates
      video_scenes/         # 3 explainer video scene JSON templates
    java/com/sdtech/studio/
      MainActivity.kt
      SDStudioApplication.kt
      audio/                # EBU R128 loudness measurement & AAC normalizer
      brand/                # LogoImageProcessor & BrandBackupManager (ZIP)
      data/                 # Room database (v2), entities, DAOs, repositories, DataStore, seeders
      di/                   # Manual AppContainer
      export/               # ImageExportManager, VideoExportPipeline, VideoExportService, ExportPolishOverlay
      planner/              # PlannerReminders (AlarmManager + notifications)
      render/               # TemplateModels, TemplateRenderer, LogoPlacementRules, TemplateRepository
      ui/
        components/         # BrandButton, BrandScaffold, EmptyState, RatioChip, SDIcon, ScreenScaffold, SectionHeader
        editor/             # EditorViewModel
        navigation/         # AppNavigation (5 bottom tabs + 8 detail routes)
        screens/            # Home, TemplateGallery, TemplateEditor, CaptionEditor, VideoHub, VideoScriptEditor,
                            # VoiceOverRecorder, QuickPreview, VideoExport, Library, Exports, Brand, Planner, Settings
        theme/              # Color.kt, Theme.kt, Type.kt
      video/                # VideoProjectModels, ScriptSceneSplitter, VideoDurationCalculator
    res/
      drawable/             # Vector icons & launcher marks
      font/                 # Bundled static TTF families (Space Grotesk, Inter, JetBrains Mono)
      values/               # strings.xml, colors.xml, themes.xml
      xml/                  # file_paths.xml
  src/debug/
    java/com/sdtech/studio/ui/screens/
      TemplateDebugScreen.kt # Debug-only 4-ratio renderer preview screen
```

## UI and navigation

- `MainActivity` is the single activity and applies `SDStudioTheme` (Dark theme is the default).
- `AppNavigation` owns the `NavHostController` and the **five-item bottom navigation bar**:
  1. **Home** (`home` → `HomeScreen`)
  2. **Posts** (`studio` → `TemplateGalleryScreen`)
  3. **Videos** (`video-hub` → `VideoHubScreen`)
  4. **Library** (`library` → `LibraryScreen` with Exports and Drafts tabs)
  5. **Brand** (`brand` → `BrandScreen`)
- Additional full-screen routes (bottom bar hidden):
  - `template-editor/{templateId}` → `TemplateEditorScreen`
  - `caption-editor/{projectId}` → `CaptionEditorScreen`
  - `planner` → `PlannerScreen` (opened from Home top bar or QuickTile)
  - `settings` → `SettingsScreen` (opened from Home top bar)
  - `video-script?projectId={projectId}` → `VideoScriptEditorScreen`
  - `voice-over/{projectId}` → `VoiceOverRecorderScreen`
  - `quick-preview/{projectId}` → `QuickPreviewScreen`
  - `video-export/{projectId}` → `VideoExportScreen`
- `PlaceholderScreens.kt` was removed as dead code, and `TemplateDebugScreen.kt` lives in `app/src/debug/` so it never ships in release builds.

## Theme and typography

- `ui/theme/Color.kt` defines the official SD Technologies brand palette.
- `ui/theme/Theme.kt` provides Material 3 dark and light color schemes, defaulting to dark mode (`DARK`) via `SettingsRepository`.
- `ui/theme/Type.kt` centralizes the type scale and maps **Space Grotesk** to headings (`display*`, `headline*`, `titleLarge`), **Inter** to body and medium/small titles (`titleMedium`, `titleSmall`, `body*`), and **JetBrains Mono** to technical labels (`label*`).
- Bundled static TTF resources live directly in `res/font/`, and OFL licence texts are retained in `app/src/main/assets/font-licenses/`.

## Startup, StrictMode, and crash logging

1. In debug builds (`BuildConfig.DEBUG`), `SDStudioApplication.onCreate()` enables `StrictMode` (`ThreadPolicy` detecting disk reads/writes and network access with `penaltyLog()`, and `VmPolicy` detecting leaked SQLite cursors, closeables, and activity leaks).
2. `SDStudioApplication.onCreate()` installs `CrashLogger` as the default uncaught exception handler.
3. When an uncaught exception occurs, the handler writes the thread name and full stack trace to the app-private file `filesDir/last_crash.txt` and clears the `crash_prompt_seen` marker.
4. `MainActivity` checks `CrashLogger.consumeCrashPrompt(application)` once on launch and displays a recovery `AlertDialog` with Copy/Dismiss actions. Users can also inspect or clear the crash log anytime in `SettingsScreen`.

## Local data architecture

### Persistence

- Room database: `sd_studio.db`, version 2 (with explicit non-destructive `MIGRATION_1_2`), containing seven entities: `BrandKit`, `LogoAsset`, `Project`, `ExportRecord`, `CaptionTemplate`, `HashtagGroup`, and `PlannerItem`.
- `BrandKit.id` is fixed to `1` to enforce the single-row brand kit.
- `ExportRecord.projectId` references `Project.id` with cascade deletion; saving a project uses an upsert transaction (`ProjectDao.save`) so existing `ExportRecord` rows are preserved.
- `PlannerItem.projectId` is nullable and uses `SET_NULL` when its project is deleted.
- Palette colors and hashtag lists are encoded with `kotlinx.serialization` JSON. Enums are stored by stable enum names.
- No destructive migration fallback is enabled.

### Repositories and state

- `BrandKitRepository` exposes the active brand kit and logo assets as Flows.
- `ProjectRepository` exposes the project list and individual project streams.
- `ContentLibraryRepository` exposes caption and hashtag collections, including division filters.
- `ExportRepository` exposes export history globally and per project.
- `PlannerRepository` exposes all and unposted planner items.
- `SettingsRepository` uses Preferences DataStore for theme mode (`DARK` default), onboarding completion, last-used ratio (`9:16` default), default logo placement mode (`SAFE` default), and the first-launch seed marker.

### Startup and first-launch seed

`SDStudioApplication` creates the manual `AppContainer`, then launches `FirstLaunchSeeder.seedIfNeeded()` on an application-owned IO coroutine scope. The seeder checks the DataStore marker, seeds the official SD Technologies `BrandKit`, installs the four bundled brand logo variants (`FULL`, `ICON`, `LIGHT`, `DARK`) via `LogoSeeder`, inserts five caption templates and four division hashtag groups inside a Room transaction, and marks seeding complete.

## Brand tab and asset processing

- `ui/screens/BrandScreen.kt` is the Brand Kit workspace. It observes Room Flows for the singleton `BrandKit`, `LogoAsset`s, `CaptionTemplate`s, and `HashtagGroup`s.
- Android Photo Picker imports logo images without broad storage permissions. `brand/LogoImageProcessor.kt` decodes/downscales to a maximum 2048 px edge, removes background pixels only when connected to an image edge and within tolerance of the four sampled corner colours, supports visible-pixel auto-cropping, and writes PNGs under app-private `files/brand/logos/`. All pixel processing runs off the main thread.
- Brand palette edits accept six-digit hex values only when Brand Lock is off; Brand Lock restricts colour editors to the official palette. Reset restores the five official SD Technologies colours.
- `brand/BrandBackupManager.kt` exports/imports a ZIP containing `brand-kit.json` plus logo PNGs on `Dispatchers.IO`.

## Template rendering engine and Post Studio

- 12 post templates live in `app/src/main/assets/templates/` and 3 video scene templates live in `app/src/main/assets/video_scenes/`.
- Layer geometry and ratio-specific geometry overrides are percentages of the selected `RatioProfile` canvas (`1:1` 1080×1080, `4:5` 1080×1350, `9:16` 1080×1920, `16:9` 1920×1080). All sizes scale relative to a 360-unit reference of the shorter canvas side so exports are density-independent across devices.
- `TemplateRenderer` renders to an Android `Bitmap` off the main thread with `Canvas`, `Paint`, gradients, image fit/crop, dark overlays, resource fonts, and `StaticLayout` text auto-fit. Light backgrounds automatically tint white logo pixels to brand navy (`#101C2C`) unless an explicit `LIGHT`/`DARK` variant is used.
- `LogoPlacementRules` centralizes `SAFE` and `TIGHT` top-right placement across all four aspect ratios.
- Never call `Bitmap.recycle()` on a bitmap that may still be drawn by Compose UI (`TemplateEditorScreen`, `TemplateGalleryScreen`, `VideoScriptEditorScreen`, `QuickPreviewScreen`); only off-screen intermediate/export bitmaps are recycled after file write or scaling.

## Video voice-over recording and loudness normalization

- Voice takes are recorded with `MediaRecorder` as `.m4a` (AAC-LC, mono, 44.1 kHz, 128 kbps) under `files/voiceovers/<projectId>/`.
- Loudness normalization (`audio/EbuR128Loudness.kt` and `audio/AndroidAacLoudnessNormalizer.kt`) uses ITU-R BS.1770 / EBU R128 K-weighting, 400 ms blocks with 75% overlap, `-70 LUFS` absolute gate, `-10 LU` relative gate, and 4-phase windowed-sinc true-peak estimation.
- Target speech loudness is `-16 LUFS` stereo-equivalent (including `+3.0103 dB` dual-mono compensation), with a `-1.5 dBTP` PCM ceiling, `+18 dB` maximum boost, and verified final AAC true peak `<= -1.0 dBTP`. Original takes are preserved and restorable.

## Explainer video quick preview and Media3 export

- `QuickPreviewScreen` renders 540 px scene previews with `LogoPlacementMode.SAFE`, plays scene voice-over files sequentially, supports timeline scrubbing, and warns if total duration exceeds 60 seconds.
- `VideoExportPipeline` and `VideoExportService` use AndroidX Media3 `1.11.1` (`media3-transformer`, `media3-effect`, `media3-common`) with a video sequence, a synchronized audio sequence (with `experimentalSetForceAudioTrack(true)` when starting with a silence gap), a top-right `BitmapOverlay` for the brand logo, optional `ExportPolishOverlay` (`CanvasOverlay`) for timestamp-driven burned-in captions and bottom teal progress bar, and optional scene-ducked background music via `GainProcessor` and `ChannelMixingAudioProcessor`.
- `VideoExportService` enforces a process-wide `AtomicBoolean` export lock, logs memory telemetry (`SDStudioExportMemory`), and saves finished MP4s to `Movies/SD Studio` via `MediaStore`.

## Performance and stability layer (`com.sdtech.studio.performance`)

- **`CentralBitmapPool` (`CentralBitmapPool.kt`)**: Single access-ordered LRU bitmap cache sized to **1/8 of available JVM heap** (`Runtime.getRuntime().maxMemory() / 1024 / 8` KB). Shared across `TemplateRenderer` (logo decoding & light-background navy tinting), `TemplateGalleryScreen` thumbnails, `TemplateEditorScreen` live previews, `EditorViewModel` user-imported slot photos, `QuickPreviewScreen` scene previews, `VideoScriptEditorScreen` scene thumbnails, `ExportsScreen` thumbnails, and `BrandScreen` logo tiles. Never calls `Bitmap.recycle()` on cached or UI-visible bitmaps.
- **`RenderDispatcher` (`RenderDispatcher.kt`)**: Single central rendering dispatcher (`Dispatchers.Default.limitedParallelism(2)`) with cooperative cancellation (`withRenderContext` + layer-level interruption checks in `TemplateRenderer`) so scrolling or leaving a screen cancels queued and in-flight renders immediately.
- **`MemoryGuard` (`MemoryGuard.kt`)**: `ComponentCallbacks2` listener registered in `SDStudioApplication` that checks `ActivityManager.isLowRamDevice` and responds to `onTrimMemory` / `onLowMemory` by trimming or evicting `CentralBitmapPool` and lowering preview resolution automatically (`NORMAL` 1.0×, `MODERATE` 0.75×, `CRITICAL` 0.50×).
- **`ThermalGuard` (`ThermalGuard.kt`)**: Listens to `PowerManager` thermal status (`NOMINAL`, `WARM`, `HOT`, `CRITICAL`), lowers preview resolution when warm/hot, and warns the user with a confirmation dialog before starting post or video exports when `shouldWarnBeforeExport` is true.
- **`ErrorBoundary` (`ErrorBoundary.kt`)**: Global recoverable error boundary for all render and export operations. Any non-cancellation exception is logged to `filesDir/last_crash.txt` via `CrashLogger.recordHandledException` and surfaced in `MainActivity` via `ErrorBoundaryDialog` with Retry and Dismiss actions — never a hard crash.
- **Baseline Profile & Benchmarks**: `app/src/main/baseline-prof.txt` pre-compiles startup, navigation, gallery, editor, and renderer hot paths. `docs/PERFORMANCE.md` and `PerformanceAndStabilityTest` enforce measurable budgets in CI.

## R8, Lint baseline, and build reproducibility

- Release builds enable `isMinifyEnabled = true` and `isShrinkResources = true` with explicit keep rules in `app/proguard-rules.pro` for Room, `kotlinx.serialization`, and enums.
- Android Lint is configured with `lint-baseline.xml` (`app/lint-baseline.xml`) and `checkReleaseBuilds = true`.
- The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) is included in the repository.
