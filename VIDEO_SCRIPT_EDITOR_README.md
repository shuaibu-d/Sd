# Explainer Video Script Editor

This update adds a local-first explainer-video project model and script editor.

## Project payload

Video projects continue to use the existing `Project` Room entity. The serialized `VideoProjectPayload` is stored in `Project.payloadJson` and contains an ordered list of `VideoScene` records. Each scene persists its type, text, text style, background type and URI, voice path and duration, manual duration, and transition.

## Scene templates

Three renderer-compatible JSON templates live in `app/src/main/assets/video_scenes/`:

- `scene_statement.json` — Big Statement / Highlighted Keyword visual.
- `scene_bullet.json` — Bullet Point visual.
- `scene_outro.json` — Outro with monogram, CTA, and Brand Kit contact line.

`TemplateRepository.loadSceneTemplates()` loads these separately from the post gallery so they do not appear as ordinary social-post templates.

## Editor behavior

- New video starts at 9:16 and supports 4:5, 1:1, and 16:9.
- Script splitting recognizes blank-line paragraphs and sentence endings, then chunks long sentences to at most 18 words per generated scene.
- Scene cards render low-resolution thumbnails with `TemplateRenderer` and SAFE logo placement.
- Scene list supports long-press drag reorder, adding and deleting scenes, splitting, merging, text/style editing, background image/video selection, and NONE/FADE transitions.
- INTRO and OUTRO scenes are created by default, editable, and removable.
- Drafts save locally to Room by serializing the complete scene model into `Project.payloadJson`.
- OpenDocument grants persisted URI access for chosen image/video backgrounds where the document provider supports it.

The video clip background currently uses its first available frame for the scene thumbnail. Full video timeline playback, voice recording, and final video rendering/export are separate future tasks.
