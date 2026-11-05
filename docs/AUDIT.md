# SD Studio — Repository Audit

**Date:** 3 November 2026  
**Package:** `com.sdtech.studio` (`:app`, `minSdk = 26`, `targetSdk = 36`)

---

## 1. Navigation Routes & Screen Reachability (`AppNavigation.kt`)

`AppNavigation` uses a 5-tab bottom navigation bar (`Home`, `Posts`, `Videos`, `Library`, `Brand`) plus 8 detail/workflow routes.

| Route | Screen / Composable | Type | Reachable From | Status |
|---|---|---|---|---|
| `home` | `HomeScreen` | Bottom Tab 1 ("Home") | Start destination, bottom nav bar | **Reachable & Working** — Launches post gallery, new video script editor, Planner, Settings, Brand Kit, Library, and recent Post/Video drafts. |
| `studio` | `TemplateGalleryScreen` | Bottom Tab 2 ("Posts") | Bottom nav bar, Home ("New post" button) | **Reachable & Working** — Loads 12 bundled post templates, ratio/theme/category filters, favourites, and opens `template-editor/{templateId}`. |
| `video-hub` | `VideoHubScreen` | Bottom Tab 3 ("Videos") | Bottom nav bar | **Reachable & Working** — Lists saved video drafts, deletes drafts, and starts or resumes `video-script`. |
| `library` | `LibraryScreen` (`ExportsContent` + Drafts) | Bottom Tab 4 ("Library") | Bottom nav bar, Home ("Library" quick tile, "See all") | **Reachable & Working** — Displays exported images/videos (`ExportsContent` with Open/Share/Edit again/Delete) and saved project drafts. |
| `brand` | `BrandScreen` | Bottom Tab 5 ("Brand") | Bottom nav bar, Home ("Brand Kit" quick tile, logo prompt banner) | **Reachable & Working** — Full Brand Kit editor (logo import/flood-fill background removal/crop, official palette + Brand Lock, contact details, caption templates, hashtag groups, ZIP backup/restore). |
| `template-editor/{templateId}` | `TemplateEditorScreen` | Detail Route | `TemplateGalleryScreen`, `HomeScreen` (post draft), `LibraryScreen` (post draft or "Edit again") | **Reachable & Working** — Live `TemplateRenderer` preview, Text/Photos/Design/Slides tabs, undo/redo, auto-save, multi-ratio PNG/JPEG export, and caption editor link. |
| `caption-editor/{projectId}` | `CaptionEditorScreen` | Detail Route | `TemplateEditorScreen` ("Caption" button) | **Reachable & Working** — Attaches/auto-saves caption to Room project, inserts Brand Kit caption templates/hashtag groups/contact line/CTA, and copies to clipboard. |
| `planner` | `PlannerScreen` | Detail Route | `HomeScreen` (top bar calendar icon & "Planner" quick tile) | **Reachable & Working** — Local month/list content calendar, platform & status tracking, 5-point posting checklist, and `AlarmManager` local notifications. |
| `settings` | `SettingsScreen` | Detail Route | `HomeScreen` (top bar settings icon) | **Reachable & Working** — Theme mode (`SYSTEM`/`LIGHT`/`DARK`), default canvas ratio, default logo placement (`SAFE`/`TIGHT`), storage metrics, Clear Cache, and Crash Log viewer. |
| `video-script?projectId={projectId}` | `VideoScriptEditorScreen` | Detail Route | `HomeScreen` ("New video" button, video draft), `VideoHubScreen`, `LibraryScreen` (video draft) | **Reachable & Working** — Script auto-splitter (18-word scene cap), scene reorder/add/delete/split/merge, scene text/style/background editor, and routes to `voice-over` and `quick-preview`. |
| `quick-preview/{projectId}` | `QuickPreviewScreen` | Detail Route | `VideoScriptEditorScreen` ("Quick preview" button) | **Reachable & Working** — Renders 540 px scene previews with SAFE logo placement, plays scene voice-overs sequentially with `MediaPlayer`, timeline scrubbing, >60s Reels warning, and routes to `video-export`. |
| `video-export/{projectId}` | `VideoExportScreen` | Detail Route | `QuickPreviewScreen` ("Export" top-bar action) | **Reachable & Working** — Configures export preset (Reels/Feed/Square/YouTube), 720p fallback, logo variant/placement, burn-in captions, progress bar, background music + auto-ducking, and runs foreground `VideoExportService`. |
| `voice-over/{projectId}` | `VoiceOverRecorderScreen` | Detail Route | `VideoScriptEditorScreen` ("Record voice-over" button) | **Reachable & Working** — Per-scene AAC/M4A voice recording with 3s countdown, live waveform, playback, EBU R128 loudness normalization (`-16 LUFS` target / `-1.0 dBTP` verified ceiling), and manual duration override. |

---

## 2. Placeholders & Dead Code Found

1. **`PlaceholderScreens.kt` (Removed)**:
   - Contained `StudioScreen(onOpenTemplateDebug: () -> Unit)` and a duplicate 1-parameter `LibraryScreen(onEditAgain: (String) -> Unit)`.
   - Neither composable was referenced anywhere in `AppNavigation.kt` or the rest of the codebase (`studio` routes to `TemplateGalleryScreen`, and `library` routes to `LibraryScreen` in `LibraryScreen.kt`).
   - **Action taken:** Deleted `PlaceholderScreens.kt`.
2. **`TemplateDebugScreen.kt` (Moved to `src/debug`)**:
   - Diagnostic 4-ratio renderer preview screen (`TemplateDebugScreen`). It is not wired to any production route in `AppNavigation.kt`.
   - **Action taken:** Moved from `app/src/main/java/com/sdtech/studio/ui/screens/TemplateDebugScreen.kt` to `app/src/debug/java/com/sdtech/studio/ui/screens/TemplateDebugScreen.kt` so it never ships in release builds.

---

## 3. UI Features Shown That Are Not Implemented End-to-End or Have Limitations

1. **Scene Transition Selector in `VideoScriptEditorScreen.kt` (`None` / `Fade`)**:
   - **Where shown:** `VideoScriptEditorScreen.kt` (lines 347–350) renders two selectable `FilterChip`s (`"No transition"` and `"Fade"`) that persist `SceneTransition.NONE` or `SceneTransition.FADE` into `Scene.transition`.
   - **Actual behavior:** Neither `QuickPreviewScreen` nor `VideoExportPipeline` reads `scene.transition`. Exported videos always use hard cuts, and `VideoExportScreen` explicitly shows `"Cross-fade scene transitions"` as a disabled switch under `"Not enabled in this stable export build"`.
   - **Risk:** Violates Engineering Rule #4 ("Never show a feature in the UI unless it works end to end. Unfinished features are hidden or labelled 'Coming soon'"). Selecting `"Fade"` in the script editor gives the false impression that the scene will fade during preview or export.
2. **Video Clip Background + Scene Text in `VideoScriptEditorScreen.kt` / `VideoExportPipeline.kt`**:
   - **Where shown:** `VideoScriptEditorScreen.kt` allows a scene to have both scene text (`scene.text`) and a `VIDEO_CLIP` background (`scene.backgroundType == SceneBackgroundType.VIDEO_CLIP`). Furthermore, `VideoScriptEditorScreen` and `QuickPreviewScreen` composite the scene text onto the first frame of the video clip in their still thumbnails.
   - **Actual behavior:** In `VideoExportPipeline.kt` (lines 152–173), when `scene.backgroundType == SceneBackgroundType.VIDEO_CLIP`, the pipeline adds the clipped video `MediaItem` directly to `videoSequence` without rendering the scene's `TemplateRenderer` text overlay on top of the video clip (unless the user separately enables "Burn-in captions" on the export screen).
3. **Video Clip Playback in `QuickPreviewScreen.kt`**:
   - **Where shown:** `QuickPreviewScreen` plays through the timeline of scenes.
   - **Actual behavior:** Scenes with `SceneBackgroundType.VIDEO_CLIP` display a static first frame (`getFrameAtTime(0)`) rather than playing the moving video clip during preview.
4. **Disabled Switches in `VideoExportScreen.kt`**:
   - **Where shown:** Under `"Not enabled in this stable export build"`, three disabled switches are displayed (`Text entrance animations · static text fallback`, `Cross-fade scene transitions`, `Final-mix loudness normalisation`).
   - **Actual behavior:** Intentionally disabled (`enabled = false`) with an explanatory note, though they could be labelled `"Coming soon"` or hidden until implemented.
5. **Legacy Media APIs Still Used Outside `VideoExportPipeline`**:
   - While `VideoExportPipeline` uses AndroidX Media3 `Transformer` and `OverlayEffect`, `MediaMetadataRetriever` is still used for duration/frame extraction in `VideoExportPipeline.kt`, `VideoScriptEditorScreen.kt`, `QuickPreviewScreen.kt`, and `ExportsScreen.kt`; `MediaPlayer` is used in `QuickPreviewScreen.kt` and `VoiceOverRecorderScreen.kt`; and `MediaExtractor`/`MediaCodec`/`MediaMuxer` are used in `AndroidAacLoudnessNormalizer.kt` for PCM loudness normalization.

---

## 4. Bundled Font Audit (`app/src/main/res/font/`)

All 39 `.ttf` files in `app/src/main/res/font/` were audited against `ui/theme/Type.kt`, `render/TemplateRenderer.kt`, and all template JSON files in `assets/templates/` and `assets/video_scenes/`:

- **Active in `Type.kt` `SDStudioTypography` and/or `TemplateRenderer.kt` (9 files, ~1.84 MB)**:
  - Space Grotesk: `space_grotesk_medium.ttf`, `space_grotesk_semibold.ttf`, `space_grotesk_bold.ttf`
  - Inter: `inter_regular.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`
  - JetBrains Mono: `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf`, `jetbrains_mono_bold.ttf`
- **Registered in `Type.kt` `FontFamily` declarations only, never referenced in `SDStudioTypography` or `TemplateRenderer.kt` (13 files, ~3.66 MB)**:
  - Space Grotesk: `space_grotesk_light.ttf`, `space_grotesk_regular.ttf`
  - Inter: `inter_thin.ttf`, `inter_extralight.ttf`, `inter_light.ttf`, `inter_medium.ttf`, `inter_extrabold.ttf`, `inter_black.ttf`
  - JetBrains Mono: `jetbrains_mono_thin.ttf`, `jetbrains_mono_extralight.ttf`, `jetbrains_mono_light.ttf`, `jetbrains_mono_semibold.ttf`, `jetbrains_mono_extrabold.ttf`
- **Completely unreferenced in any Kotlin or XML/JSON file (17 italic `.ttf` files, ~5.23 MB)**:
  - Inter Italics (9 files): `inter_thin_italic.ttf`, `inter_extralight_italic.ttf`, `inter_light_italic.ttf`, `inter_regular_italic.ttf`, `inter_medium_italic.ttf`, `inter_semibold_italic.ttf`, `inter_bold_italic.ttf`, `inter_extrabold_italic.ttf`, `inter_black_italic.ttf`
  - JetBrains Mono Italics (8 files): `jetbrains_mono_thin_italic.ttf`, `jetbrains_mono_extralight_italic.ttf`, `jetbrains_mono_light_italic.ttf`, `jetbrains_mono_regular_italic.ttf`, `jetbrains_mono_medium_italic.ttf`, `jetbrains_mono_semibold_italic.ttf`, `jetbrains_mono_bold_italic.ttf`, `jetbrains_mono_extrabold_italic.ttf`
