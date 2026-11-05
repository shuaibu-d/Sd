# SD Studio Post Editor

The template editor now uses one `EditorViewModel` per selected template. The ViewModel owns immutable `EditorDraft` state and persists it through the existing Room-backed `ProjectRepository` as a `ProjectStatus.DRAFT` record.

## Included

- Live `TemplateRenderer` preview with a 320 ms debounce.
- Editable field for every text layer, plus Heading / Body / Label presets.
- Brand Lock styling using the template's brand tokens and the saved Brand Kit palette.
- Android photo picker for each image slot; selected image files are copied into app-private storage and referenced by the draft.
- Crop / Fit selection and per-slot dark overlay slider.
- Light / dark theme, all four ratios, Safe / Tight logo placement, logo variant, and safe-zone guide toggle.
- Undo / redo history capped at 20 snapshots.
- Carousel drafts with up to 10 slides, add / duplicate / delete, press-and-drag reorder, shared canvas settings, and slide counter values.
- Draft auto-save 1.8 seconds after edits, save on lifecycle stop / screen exit, and restore from Room when the editor is reopened after process death.

## Draft storage

Drafts use a deterministic project key (`template-draft-<templateId>`) so the navigation route can recover the same Room record after Android recreates the process. `payloadJson` contains the serializable editor draft. Photo files are copied to `filesDir/editor_media/` and their app-private paths are stored in each slide.

## Build verification

The source changes and template JSON files were inspected, but this environment does not contain a Gradle executable or Gradle wrapper, so an Android Gradle build could not be run here. Run the project's normal Gradle build in the Android build environment before merging or publishing.

## Image export and sharing

- Export PNG (default) or JPEG at quality 92 using the renderer's full-size ratio profiles (1080×1080, 1080×1350, 1080×1920, and 1920×1080).
- Save to `Pictures/SD Studio` through MediaStore with `RELATIVE_PATH` and `IS_PENDING` on Android 10+.
- On Android 8 and 9, request the legacy `WRITE_EXTERNAL_STORAGE` runtime permission, write into the public Pictures directory, and notify MediaStore with `MediaScannerConnection`.
- Batch export can select any combination of 1:1, 4:5, 9:16, and 16:9. Carousel exports write every slide sequentially with `_slide_01`, `_slide_02`, etc. in the filename.
- Every successful image is recorded in the existing Room `ExportRecord` table and linked to its draft Project. The latest editor draft is synchronously persisted before rendering, so Edit again restores the version that was exported.
- Each full-size render is completed and its bitmap recycled before the next render begins. Export thumbnails are decoded at reduced resolution in the Library.
- Sharing stages a copy in app cache and uses AndroidX `FileProvider` with read permission. The export sheet can optionally copy the post caption to the clipboard.
- Library → Exports lists saved files in a two-column grid with Open, Share, Delete, and Edit again actions.

No broad storage permission is requested on Android 10 and newer. `WRITE_EXTERNAL_STORAGE` is declared with `maxSdkVersion=28` only for Android 8/9 compatibility.
