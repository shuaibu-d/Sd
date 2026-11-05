# SD Studio — Documentation Index

All technical architecture, progress logs, audit reports, and feature specifications for **SD Studio** (`com.sdtech.studio`) by **SD Technologies** (*"Create Without Limits."*).

## Core Documentation

- **[`ARCHITECTURE.md`](ARCHITECTURE.md)** — Authoritative technical architecture: 5-tab navigation (`Home`, `Posts`, `Videos`, `Library`, `Brand`) + 8 detail routes, typography & theme system, Room database (`sd_studio.db`), Brand Kit & logo processing, deterministic `TemplateRenderer`, voice-over EBU R128 loudness normalization, and Media3 `Transformer` video export pipeline.
- **[`PROGRESS.md`](PROGRESS.md)** — Chronological implementation progress log, hardening history, and repository audit record.
- **[`AUDIT.md`](AUDIT.md)** — Complete navigation route audit, dead-code removal summary, UI-vs-implementation gap analysis, and bundled font asset audit.
- **[`PERFORMANCE.md`](PERFORMANCE.md)** — Measurable cold start, gallery, editor, and memory budgets, `com.sdtech.studio.performance` layer architecture, and recorded benchmark/stress-test numbers.

## Feature Guides

- **[`TEMPLATE_GALLERY.md`](TEMPLATE_GALLERY.md)** — The 12 bundled post templates, category/ratio/theme filtering, LRU thumbnail caching, and favourites.
- **[`POST_EDITOR.md`](POST_EDITOR.md)** — `EditorViewModel`, carousel slides, live preview, undo/redo, Brand Lock, and multi-ratio PNG/JPEG export to `Pictures/SD Studio`.
- **[`VIDEO_SCRIPT_EDITOR.md`](VIDEO_SCRIPT_EDITOR.md)** — Script-to-scene splitting (18-word cap), 3 video scene templates (`scene_statement`, `scene_bullet`, `scene_outro`), scene ordering, and background media selection.
- **[`VOICE_OVER.md`](VOICE_OVER.md)** — Per-scene AAC/M4A voice recording, live waveform, and ITU-R BS.1770 / EBU R128 loudness normalization (`-16 LUFS` target, `-1.0 dBTP` true-peak verification).
- **[`QUICK_PREVIEW.md`](QUICK_PREVIEW.md)** — Local 540 px scene timeline preview player with voice-over playback and scrubbing.
- **[`VIDEO_EXPORT.md`](VIDEO_EXPORT.md)** — AndroidX Media3 `Transformer` 1.11.1 export presets, foreground service execution, and SAFE/TIGHT logo overlay placement.
- **[`VIDEO_EXPORT_POLISH.md`](VIDEO_EXPORT_POLISH.md)** — Timestamp-driven burned-in captions, bottom teal progress bar (`CanvasOverlay`), and scene-ducked background music (`GainProcessor`).
- **[`PLANNER.md`](PLANNER.md)** — Local content calendar (month/list views), 5-point posting checklist, caption editor, and `AlarmManager` reminder notifications.
- **[`RELEASE_FROM_PHONE.md`](RELEASE_FROM_PHONE.md)** — Instructions for generating a release keystore and configuring GitHub Actions secrets for signed AAB builds.

## Root Policy & Store Files

- **[`../PRIVACY_POLICY.md`](../PRIVACY_POLICY.md)** — Privacy policy for SD Studio.
- **[`../PLAY_STORE_LISTING.md`](../PLAY_STORE_LISTING.md)** — Google Play Store listing copy and metadata.
