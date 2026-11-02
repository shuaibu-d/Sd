# SD Studio — Architecture

## Project baseline

- **Application ID / namespace:** `com.sdtech.studio`
- **Module:** `:app` only
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **SDK levels:** compileSdk 37, targetSdk 37, minSdk 26
- **Build:** Gradle Kotlin DSL, version catalog, GitHub Actions
- **Navigation:** Navigation Compose `2.10.2`
- **Brand:** Midnight Navy (`#101C2C`), Innovation Teal (`#16A6A0`), Premium Gold (`#D9B45B`), Cloud White (`#F3F5F8`), and Slate Grey (`#263445`)

## Current structure

```text
app/
  src/main/
    AndroidManifest.xml
    java/com/sdtech/studio/
      MainActivity.kt
      SDStudioApplication.kt
      ui/
        components/
          BrandButton.kt
          EmptyState.kt
          RatioChip.kt
          ScreenScaffold.kt
          SectionHeader.kt
        navigation/
          AppNavigation.kt
        screens/
          HomeScreen.kt
          PlaceholderScreens.kt
        theme/
          Color.kt
          Theme.kt
          Type.kt
    res/
      values/
        strings.xml
      font/                 # Bundled static TTF families
    assets/font-licenses/   # OFL licence texts for bundled fonts
```

## UI and navigation

- `MainActivity` is the only activity and applies `SDStudioTheme`.
- `AppNavigation` owns the Navigation Compose controller and the four-item bottom navigation bar.
- Home, Studio, Library, and Brand are separate composable destinations.
- `ScreenScaffold` provides consistent page spacing and optional vertical scrolling.
- `BrandButton`, `SectionHeader`, `EmptyState`, and `RatioChip` are reusable UI building blocks.
- Home's two creation cards route to the Studio placeholder until their dedicated creation flows are implemented.
- All user-facing copy is stored in `res/values/strings.xml`.

## Theme and typography

- `ui/theme/Color.kt` defines the brand palette.
- `ui/theme/Theme.kt` provides Material 3 light and dark color schemes and follows the system theme by default.
- `ui/theme/Type.kt` centralizes the type scale and maps Space Grotesk to headings, Inter to body text, and JetBrains Mono to technical labels.
- Bundled static TTF resources live directly in `res/font/`; Android resource directories cannot contain nested font subfolders.
- Font licence texts are retained in `app/src/main/assets/font-licenses/`.
- The intended bundled font files belong in `app/src/main/res/font/` and will be connected in `Type.kt` once uploaded.

## Startup and crash logging

1. `SDStudioApplication.onCreate()` installs `CrashLogger` as the default uncaught exception handler.
2. When an uncaught exception occurs, the handler writes the thread name and full stack trace to the app-private file `filesDir/last_crash.txt`.
3. The previous platform exception handler is invoked afterward so Android retains its normal crash handling.
4. `MainActivity` reads the report once on launch. The file is deleted after the read attempt.
5. If a report exists, Compose displays an `AlertDialog` with the stack trace and Copy/Dismiss actions.

Crash reports remain inside the app's private internal storage and are not uploaded.

## Build pipeline

- GitHub Actions checks out the repository and configures Temurin JDK 17.
- `gradle/actions/setup-gradle` installs the pinned Gradle distribution.
- The workflow installs Gradle `9.7.0` and runs `gradle assembleDebug`, without requiring a committed Gradle wrapper JAR.
- The generated debug APK is uploaded as the `SD-Studio-debug` workflow artifact.

## Planned direction

Introduce MVVM with ViewModel and StateFlow, Room (KSP), DataStore, kotlinx.serialization, Coil, and Media3 as their corresponding features are implemented. Add dependencies only when needed.


## Bundled typography assets

- Space Grotesk: Light, Regular, Medium, SemiBold, Bold.
- Inter: Thin, ExtraLight, Light, Regular, Medium, SemiBold, Bold, ExtraBold, Black, with italic variants bundled.
- JetBrains Mono: Thin, ExtraLight, Light, Regular, Medium, SemiBold, Bold, ExtraBold, with italic variants bundled.
- All font files are static TTFs, avoiding runtime downloads and ensuring typography works offline.


## Local data architecture

### Persistence

- Room database: `sd_studio.db`, version 1, with seven entities: `BrandKit`, `LogoAsset`, `Project`, `ExportRecord`, `CaptionTemplate`, `HashtagGroup`, and `PlannerItem`.
- `BrandKit.id` is fixed to `1` to enforce the single-row brand kit.
- `ExportRecord.projectId` references `Project.id` with cascade deletion.
- `PlannerItem.projectId` is nullable and uses `SET_NULL` when its project is deleted.
- Palette colors and hashtag lists are encoded with kotlinx.serialization JSON. Enums are stored by stable enum names.
- No destructive migration fallback is enabled.

### Repositories and state

- `BrandKitRepository` exposes the active brand kit and logo assets as Flows.
- `ProjectRepository` exposes the project list and individual project streams.
- `ContentLibraryRepository` exposes caption and hashtag collections, including division filters.
- `ExportRepository` exposes export history globally and per project.
- `PlannerRepository` exposes all and unposted planner items.
- `SettingsRepository` uses Preferences DataStore for theme mode, onboarding completion, last-used ratio, and the first-launch seed marker.

### Startup and first-launch seed

`SDStudioApplication` creates the manual `AppContainer`, then launches the seeder on an application-owned IO coroutine scope. The seeder checks the DataStore marker, writes missing starter data inside a Room transaction, and marks seeding complete only after the transaction succeeds. The starter brand kit is SD Technologies with the official five-color palette and locked brand settings. Five professional caption templates and four hashtag groups are inserted for Software, AI, Academy, and Digital.

### Dependency versions

- Room `2.8.5`, KSP `2.3.12`, DataStore `1.3.0`, kotlinx.serialization JSON `1.11.0`.


## Brand tab and asset processing

- `ui/screens/BrandScreen.kt` is the Brand Kit workspace. It observes Room Flows for the singleton BrandKit, logo assets, caption templates, and hashtag groups; edits are persisted through repositories.
- Android Photo Picker imports logo images without broad storage permissions. `brand/LogoImageProcessor.kt` decodes/downscales to a maximum 2048 px edge, removes background pixels only when connected to an image edge and close to one of the four sampled corner colours, supports visible-pixel auto-cropping, and writes PNGs under app-private `files/brand/logos/`. Pixel-heavy operations run off the main thread.
- The transparent preview uses a checkerboard. The four logo variants are stored independently by `LogoType` and previewed against navy and white tiles. The manual four-edge crop controls are exposed for the ICON slot to isolate a monogram.
- Brand palette edits accept six-digit hex values only when Brand Lock is off; Brand Lock keeps colour editors restricted to the official saved palette. Reset restores the five official SD Technologies colours.
- Caption and hashtag editors use existing Room tables and support create/update/delete operations.
- `brand/BrandBackupManager.kt` exports/imports a ZIP containing `brand-kit.json` plus any available logo PNGs. Import writes logos into app-private storage and applies database updates in a Room transaction. ZIP I/O and manifest parsing run on `Dispatchers.IO`.

## Template rendering engine

- JSON templates live in `app/src/main/assets/templates/` and are decoded with kotlinx.serialization using a sealed `TemplateLayer` hierarchy and the `type` discriminator.
- Layer geometry and ratio-specific geometry overrides are percentages of the selected `RatioProfile` canvas. The four supported output canvases are 1080×1080, 1080×1350, 1080×1920 and 1920×1080.
- `TemplateRenderer` renders to an Android `Bitmap` with `Canvas`, `Paint`, gradients, image fit/crop, optional image darkening, font resources, and `StaticLayout` text layout with iterative font-size reduction.
- Logo placement is centralized in `LogoPlacementRules`; renderers resolve the requested logo variant from the Brand Kit's `LogoAsset` records. SAFE/TIGHT placement values are covered by JVM unit tests.
- Safe-zone guides are optional render overlays. `TemplateDebugScreen` renders the bundled sample announcement in all four ratios; open it from Studio → Open template renderer preview.
- Rendering and sample preview generation are performed away from the main thread by the calling screen. Production callers should likewise invoke `TemplateRenderer.render` from an IO/default dispatcher and release preview bitmaps when no longer needed.


## Video voice-over recording

- Voice takes are recorded with Android `MediaRecorder` as MPEG-4 containers (`.m4a`) using AAC, mono, 44.1 kHz, 128 kbps. Files are stored under the app-private `files/voiceovers/<projectId>/` directory and referenced from each serialized video scene.
- Microphone permission is requested at the point of use with an explanation and a recovery path to Android App Info when permission is denied.
- The recorder supports a three-second countdown, live peak meter, sampled waveform, playback, re-recording, deletion, and sequential scene navigation. Recording stops and saves a usable partial take when the app moves to the background or audio focus is lost.
- A scene's effective duration is its manual duration when set; otherwise it is the recorded voice duration plus 400 ms. No take is uploaded or copied to shared storage.
- Loudness normalization uses ITU-R BS.1770 / EBU R128 K-weighting, 400 ms blocks with 75% overlap, absolute and relative gates, and four-phase windowed-sinc true-peak estimation. The app speech target is -16 LUFS stereo-equivalent. Mono takes receive the BS.1770 dual-mono +3.0103 dB compensation because video playback normally duplicates the voice channel to L/R. This is an application target, not the EBU broadcast reference of -23 LUFS.
- Normalization decodes AAC to PCM with `MediaExtractor`/`MediaCodec`, applies constrained static gain, encodes a new AAC-LC M4A, then decodes and measures the encoded output. Up to two correction passes are allowed; final AAC true peak must verify at or below -1.0 dBTP. The PCM processing ceiling is -1.5 dBTP to leave AAC codec headroom. Gain boost is capped at +18 dB; when this cap prevents reaching target, the app reports it rather than claiming the target was met.
- Original takes are retained separately; the active scene can use the normalized derivative or restore the original. Failed processing never replaces the original. This is not compression, noise reduction, or automatic dynamic-range control; if the peak ceiling prevents the target loudness, peak safety takes priority.

## Explainer video quick preview

`QuickPreviewScreen` loads the persisted `VideoProjectPayload`, renders each scene with `TemplateRenderer` at 540 px wide, and uses the same scene templates and `LogoPlacementRules` as the editor/export path. SAFE logo placement is used for video scenes. Safe-zone guides are preview overlays only and are not part of exported artwork. Scene audio is played from the active `voiceFilePath` using Android `MediaPlayer`. Timeline duration uses `manualDurationMs` when present, otherwise `voiceDurationMs + 400 ms`; unrecorded scenes use a 3-second preview fallback. Bitmaps are recycled on replacement and screen disposal. This remains an approximate preview, not a frame-accurate renderer; video backgrounds are represented by a still frame and transitions are not animated.

## Video export
The video export path uses Media3 Transformer 1.11.1 with a video sequence, a time-aligned audio sequence, and a composition-level BitmapOverlay logo. See `VIDEO_EXPORT_README.md` for presets, media handling, and limitations.

## Export polish options (Media3 1.11.1)

The export screen exposes independent, opt-in switches for captions, the bottom progress indicator, and background music. All are off by default, so an export with every polish option disabled follows the same MVP video/voice/logo composition path.

- Burned-in captions and the teal progress indicator use one full-frame `CanvasOverlay` attached through the composition-level `OverlayEffect`. It draws against the Media3 presentation timestamp, so caption changes follow the same scene-duration list used by the video and voice tracks. Captions use a dark translucent panel, brand accent, centered text, and a lower-third position above the platform UI safe area. The progress indicator is a thin brand-teal line at the bottom edge.
- Background music is imported through Android's document picker and its read permission is persisted. The selected file is clipped to the composition length, or split into sequential scene-aligned chunks and looped when Loop is enabled. `GainProcessor` applies the selected volume. When Auto-duck is enabled, music segments belonging to scenes with an existing voice-over take use 22% of the selected music gain; scenes without a voice take use the selected gain. This is scene-based ducking, not voice-activity detection inside a take. Stereo music is downmixed to mono so it can mix with the app's mono voice recordings. Music use requires the user to have the necessary rights.
- Text entrance animation is intentionally unavailable in this stable polish increment. Scene text is currently rasterized into each `TemplateRenderer` bitmap; moving or fading that full bitmap would animate the background as well as the text. A correct implementation needs a separately rendered text layer and time-dependent overlay. Applying a full-frame transform as a shortcut would produce black edges and a poor result, particularly on lower-end phones. The UI keeps the option visibly disabled and exports static text instead.
- Cross-fade is intentionally unavailable. The official Media3 Composition guide for the pinned 1.11.1 API lists crossfading video or audio tracks as unsupported. The option remains disabled rather than substituting a misleading fade-to-black or silently changing scene durations. Static cuts remain the reliable fallback.
- Final-mix loudness normalization is intentionally unavailable in this increment. The existing per-voice take normalizer measures and re-encodes individual takes, but that is not a final-mix loudness pass. A trustworthy final mix normalizer requires a post-export decode/measure/re-encode and MP4 remux workflow with verified A/V sync and true-peak limits. It is not safe to claim that a simple gain adjustment on the music track normalizes the complete voice-plus-music mix. The option remains disabled; the existing voice-over normalization metadata and files are unchanged.

These deliberate fallbacks preserve the original export when optional switches are off and avoid relying on unsupported composition transitions or unverified full-mix DSP. See the Media3 Composition limitations in the official Android documentation: https://developer.android.com/media/media3/transformer/composition.

## Release hardening and v1.1 preparation (2 November 2026)

### Export lifecycle and memory

- `VideoExportService` owns a process-wide `AtomicBoolean` export lock. `start()` uses compare-and-set so a second export request is rejected; the lock is released on completion, cancellation, failure, or service destruction. The export screen rechecks the lock after recreation and exposes cancellation while the foreground service continues.
- The pipeline estimates temporary/output space before rendering and fails early with a storage message and 720p suggestion when free space is insufficient. This is a heuristic, not a guarantee; actual encoded size varies by content and device encoder.
- New script projects are capped at 40 scenes to keep the 540 px preview bitmap cache within a practical memory budget. Still images and logo assets are bounds-decoded and power-of-two sampled to avoid decoding large originals at full dimensions. Each scene bitmap is rendered, written to a temporary PNG, and recycled before the next scene. Export output is limited to the selected 1080p or 720p preset.
- PSS and Java heap snapshots are logged under `SDStudioExportMemory` during scene preparation and every five seconds during Transformer encoding. These are diagnostic snapshots, not a full Android Studio Memory Profiler trace. Physical-device profiling remains required.
- Clear Cache is disabled while the export lock is active. It removes cache contents only; Room project data, voice recordings in private files, and completed videos in Movies/SD Studio are not intentionally removed.

### State recovery and settings

- The video script editor preserves its project ID/scalar state through saved instance state and reloads the matching Room draft before writing after activity recreation. Autosave is debounced, so a force-stop inside the 650 ms debounce window can still lose the latest edits.
- Settings are stored in DataStore and include system/light/dark theme, default ratio, and SAFE/TIGHT logo placement. Defaults are applied to new post/video editing sessions and video export. FULL/ICON remains a separate logo-asset choice.
- Crash Log retains the latest uncaught Java/Kotlin exception locally, displays a startup prompt once per recorded crash, and provides a Settings viewer/clear action. It cannot capture native process crashes or crashes before handler installation.

### R8 and signing

- The build pins AGP 9.3.1, Gradle 9.7.0 in CI, and Kotlin Gradle Plugin 2.4.21. This combination stays within Kotlin 2.4.21's documented AGP/Gradle compatibility range; AGP 9.3 supports compileSdk 37 and requires Gradle 9.5.0 or newer. CI installs SDK Build Tools 36.0.0.
- Release builds enable R8 minification and resource shrinking. `app/proguard-rules.pro` preserves Room entities/DAOs, Kotlin serialization metadata, and Android entry points. These rules are conservative; test Room, serialization, notifications, alarms, FileProvider sharing, and Media3 codecs in a signed release build.
- Signing is environment-driven. CI decodes the Base64 JKS into the runner temporary directory and supplies passwords/alias through GitHub Actions secrets. The keystore is never stored in the repository.
- Current version: `versionCode 2`, `versionName 1.1.0`. Increment versionCode for every Play upload.

### Privacy boundary

- Android backup is disabled (`allowBackup=false`) so app-private projects are not included in Android Auto Backup. Files explicitly saved to shared Movies/SD Studio storage can still be copied by the operating system or other apps. Explicit sharing transfers content to the selected destination.
- The privacy policy and Play listing are drafts. Confirm final manifest/dependencies, Play Data safety declarations, and the developer's support contact before publication.
