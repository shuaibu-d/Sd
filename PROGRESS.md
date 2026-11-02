# SD Studio — Progress

## Task 1: Project skeleton

- [x] Single-module Android project (`app`) using Kotlin DSL.
- [x] Version catalog with pinned plugin and library versions.
- [x] Jetpack Compose and Material 3 starter screen.
- [x] SD Technologies brand colors and slogan on the launch screen.
- [x] Application-level uncaught-exception logger writes `last_crash.txt` in app-private storage.
- [x] Next-launch crash dialog with stack trace, Copy, and Dismiss actions.
- [x] Android `.gitignore`.
- [x] GitHub Actions debug build using JDK 17 and installed Gradle (no wrapper required).
- [x] Upload debug APK artifact as `SD-Studio-debug`.

## Task 2: Visual foundation

- [x] Material 3 light and dark brand color schemes.
- [x] Centralized typography scale for headings, body text, and technical labels.
- [x] Single-activity bottom navigation: Home, Studio, Library, and Brand.
- [x] Placeholder destinations using the reusable `EmptyState` component.
- [x] Reusable `BrandButton`, `SectionHeader`, `EmptyState`, `RatioChip`, and `ScreenScaffold` components.
- [x] Home greeting, post/video creation cards, and empty Recent projects section.
- [x] User-facing copy centralized in `strings.xml`.
- [x] Bundle Space Grotesk (5 static weights), Inter (9 static weights), and JetBrains Mono (8 static weights) as Android TTF resources.
- [x] Bundle the supplied font licences under `app/src/main/assets/font-licenses/`.
- [x] Connect Space Grotesk to headings, Inter to body text, and JetBrains Mono to technical labels in `ui/theme/Type.kt`.

## Task 4: Brand tab and logo preparation

- [x] Replace the Brand placeholder with a working Brand Kit editor.
- [x] Import logo images with the Android system photo picker and process them at up to 2048 px.
- [x] Sample the four image corners and remove only edge-connected background pixels using flood fill and an adjustable tolerance.
- [x] Preview transparent results on a checkerboard and auto-crop to visible pixels with padding.
- [x] Add an ICON-only crop editor for isolating the monogram.
- [x] Save prepared logo variants as PNGs in app-private storage and show each on navy and white tiles.
- [x] Display and edit the official palette; reset to the official colours; enforce palette-only editing while Brand Lock is enabled.
- [x] Edit website, WhatsApp, social handle, and default CTA.
- [x] Add, edit, and delete caption templates and hashtag groups.
- [x] Export/import a ZIP backup containing JSON metadata and available logo PNG files.
- [ ] Verify the Brand screen, image processing, backup round-trip, and full Gradle build in GitHub Actions/on a phone.

## Build notes

- `compileSdk` and `targetSdk` are 37; `minSdk` is 26.
- The GitHub workflow invokes `gradle assembleDebug`; it intentionally does not use `./gradlew`.
- The visual foundation adds Navigation Compose `2.10.2` to the version catalog.
- The local execution environment used to prepare this update does not have Gradle installed, so a local APK build was not run. GitHub Actions is the build verification step after pushing.

## Next

- Verify the latest Brand tab update with GitHub Actions and on a physical Android phone.
- Implement the Studio creation flow after the Brand tab is verified.


## Font integration notes

- Space Grotesk includes Light, Regular, Medium, SemiBold, and Bold.
- Inter includes Thin through Black in the 18pt static family, including italic resources.
- JetBrains Mono includes Thin through ExtraBold in static TTF resources, including italic resources.
- The default Material 3 typography uses the normal font families; italic resources are bundled for future explicit italic styling.
- Font resources are flattened directly into `res/font/`, as required by Android resource packaging.


## Task 3: Local data layer

- [x] Add Room 2.8.5 with KSP code generation.
- [x] Add entities and DAOs for BrandKit, LogoAsset, Project, ExportRecord, CaptionTemplate, HashtagGroup, and PlannerItem.
- [x] Add kotlinx.serialization-backed converters for palette and hashtag lists, plus enum converters.
- [x] Add Flow-based repositories for brand assets, projects, content library, exports, and planner items.
- [x] Add Preferences DataStore for theme mode, onboarding completion, last-used ratio, and initial-seed state.
- [x] Add manual AppContainer and initialize it from SDStudioApplication.
- [x] Add transactional first-launch seeding for SD Technologies brand palette, five caption templates, and four division hashtag groups.
- [x] Add a repository unit test using a fake DAO.
- [ ] Verify `gradle testDebugUnitTest assembleDebug` in GitHub Actions after pushing.

### Data-layer notes

- Room database file: `sd_studio.db` in app-private storage.
- Database version: 1. No destructive migration fallback is configured, so existing user data will not be silently erased by a schema mismatch.
- The BrandKit singleton is enforced by primary key ID 1.
- Project deletion cascades to export history; planner references are set to null.
- DataStore is reserved for small preferences and seed completion; structured creative content stays in Room.

## Task 4 — Template engine

- [x] Serializable template/theme/layer models, ratio profiles and per-ratio layer overrides.
- [x] SAFE/TIGHT logo placement rules for all four canvas ratios.
- [x] Canvas/StaticLayout renderer with resource fonts, text auto-fit, image slots, overlays, shapes, contact line, Brand Kit logo selection and optional safe-zone guides.
- [x] Bundled sample announcement template and four-ratio debug preview.
- [x] JVM unit tests for logo placement calculations.
- [ ] Verify Gradle unit tests and debug APK build in GitHub Actions.

## Explainer video script editor
- Added serializable `Scene`, scene enums, and `VideoProjectPayload` persisted in `Project.payloadJson` for `ProjectType.VIDEO`.
- Added three renderer-backed scene templates in `app/src/main/assets/video_scenes/` and a dedicated `TemplateRepository.loadSceneTemplates()` loader.
- Added a 9:16-default New Video screen with all four ratios, script splitting, 18-word scene chunking/warnings, draggable scene thumbnails, scene text/style/background editing, split/merge/add/delete, transitions, and editable/removable INTRO/OUTRO scenes.
- Scene thumbnails use `TemplateRenderer`, the existing SAFE logo placement rules, brand palette/contact details, image backgrounds, and first-frame previews for video clips.
- Video drafts are saved locally in the existing Room `projects` table as `payloadJson`; no account or cloud service is used.
- Android Gradle build has not been run in this environment because neither Gradle nor a Gradle wrapper is available.


## Voice-over recorder
- Added microphone permission rationale and denied-permission recovery instructions.
- Added per-scene AAC/M4A recording, 3-second countdown, live meter, sampled waveform, playback, re-record and delete.
- Added sequential scene navigation, app-background/audio-focus interruption handling, private per-project take storage, and voice/manual scene-duration rules.
- Added quiet-room/headset tip; documented why automatic loudness normalization is not implemented.

### Voice-over loudness normalization

- Added BS.1770 / EBU R128 integrated loudness measurement with K-weighting, 400 ms / 75%-overlap gating, -70 LUFS absolute gate and -10 LU relative gate.
- Added four-phase windowed-sinc true-peak estimation, static gain planning towards -16 LUFS, -1.5 dBTP PCM ceiling and +18 dB maximum boost.
- Added Android AAC decode / PCM processing / AAC-LC re-encode pipeline using `MediaExtractor`, `MediaCodec` and `MediaMuxer`; output is decoded and measured again with bounded correction passes and a final -1.0 dBTP safety check.
- Normalized audio is stored as a separate derivative. The original take remains available and can be restored; re-record/delete clean up both files.
- Added JVM loudness-analysis tests and documented the limits of static normalization. Device-level MediaCodec verification remains necessary.

## Quick preview player
- Added a local quick-preview route from the explainer video editor.
- Renders scene templates at 540 px wide with SAFE logo placement and visible safe-zone guides.
- Plays scene voice-over files in order with `MediaPlayer`; includes play/pause, Previous/Next, scene timeline blocks, tap-to-jump, and global scrubbing.
- Scene duration uses manual timing or voice duration plus 400 ms; unrecorded scenes use a 3-second preview fallback.
- Displays total duration and warns above 60 seconds; pauses safely when the app leaves the foreground.
- Scene bitmaps are recycled when replaced and when leaving the preview screen.

## Video exporter
Added Media3 Transformer 1.11.1 export presets, synchronised voice-over sequence with silence gaps, scene image/video items, composition-level logo overlay, foreground progress/cancel service, MediaStore save, ExportRecord persistence, and Share/View UI. See `VIDEO_EXPORT_README.md`. Full Android build/device validation remains outstanding.

## Video export polish increment
- Added opt-in, timestamp-driven burned captions and a thin teal progress bar through Media3 `CanvasOverlay` / composition `OverlayEffect`.
- Added optional background music import, per-scene volume control, loop-or-trim-to-project-length behavior, mono downmix for mixing with voice-over, and scene-based ducking using Media3 `GainProcessor`.
- Kept all polish switches off by default. Text entrance animation, cross-fade and final-mix loudness normalization are visibly disabled with reasons documented in `ARCHITECTURE.md`; they are not simulated with unstable or misleading shortcuts.
- See `VIDEO_EXPORT_POLISH_README.md` for device verification steps.

## Hardening and release preparation — 2 November 2026

- Added Settings destination: theme, default ratio, SAFE/TIGHT logo mode, app/cache storage figures, Clear Cache, Crash Log viewer, About/version.
- Applied DataStore theme to the app root and applied saved ratio/logo defaults to new editing/export flows.
- Added process-wide export lock, re-entry lock display, cancellation after re-entry, and Clear Cache lock during exports.
- Added storage preflight heuristic, bounds/sample image and logo decoding, per-scene bitmap recycling, and PSS/heap snapshots (`SDStudioExportMemory`).
- Video script editor reloads the open Room draft after activity recreation and limits project/script/scene text lengths.
- Added unit tests for placement rules, script splitting, and duration calculations.
- Enabled R8 minification/resource shrinking, added conservative ProGuard rules, set versionCode 2/versionName 1.1.0, and added signed AAB GitHub Actions workflow.
- Added phone keystore/secrets instructions, privacy policy draft, and Play Store listing draft.
- Validation limitation: no full Android Gradle build, instrumented test, signed AAB run, or device memory profile was possible in the editing environment. Run the included workflow and physical-device checklist before Play upload.
