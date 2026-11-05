# Full-Screen Video Editor Workspace (v2)

This update replaces the legacy long-form script editor with a professional, CapCut/DaVinci-inspired full-screen multi-track video editor workspace while preserving the official SD Studio palette (`MidnightNavy`, `DeepNavy`, `DarkSurface`, `InnovationTeal`, `PremiumGold`, `CloudWhite`) and `Space Grotesk` / `Inter` / `JetBrains Mono` typography.

## Adaptive Workspace Architecture

- **Portrait Phone Layout (`Compact` / `360 dp+` wide)**:
  1. **Studio Top Bar**: Back button, editable project title + active aspect ratio badge, Undo, Redo, and Export CTA (all $\ge 48\text{ dp}$ touch targets with accessibility labels).
  2. **Canvas Preview (~45% height default)**: Real-time scene/playhead monitor with 9:16, 4:5, 1:1, and 16:9 framing, safe-zone overlay, and SD Technologies watermark preview.
  3. **Resizable Split Handle (`canvas_timeline_split_handle`)**: Drag handle resizing the canvas/timeline vertical split dynamically between 28% and 62%.
  4. **Transport Bar (`video_transport_bar`)**: Play/Pause, Step Back/Forward, `JetBrains Mono` timecode (`current / total`), Zoom Out / Reset / Zoom In, and Immersive Mode toggle.
  5. **7-Track Multi-Track Timeline (`multi_track_timeline`)**: Background, Video/Media, Logo/Overlay, Text, Captions, Voice-Over, and Music tracks with pinch-to-zoom, two-finger pan, magnetic snapping (`TimelineSnapEngine`) with haptic feedback, tap-to-select, long-press multi-select, drag-to-move, and $48\text{ dp}$ interactive left/right trim handles.
  6. **Bottom Context Toolbar (`bottom_context_toolbar`)**: Contextual clip action strip (`Split`, `Duplicate`, `Delete`, `Trim -/+`) plus 7 primary tool tabs (`Text`, `Media`, `Audio`, `Captions`, `Brand`, `Effects`, `Templates`) that open non-covering anchored bottom sheets below the canvas.
- **Landscape & Tablet Layout (`Medium` / `Expanded`, foldables, multi-window)**:
  - **Left Pane**: Studio Canvas Preview + Transport Bar.
  - **Right Dock (`studio_inspector_panel`)**: Persistent tabbed Inspector for `Text`, `Media`, `Audio`, `Captions`, `Brand`, `Effects`, and `Templates`.
  - **Bottom Pane**: Full-width 7-track multi-track timeline and context toolbar.

## Preserved Rules & v2 Domain Wiring

- Wired directly to `TimelineProject` and `CommandHistory` (`MoveClipCommand`, `TrimClipCommand`, `SplitClipCommand`, `DeleteClipCommand`, `AddClipCommand`, `SetPropertyCommand`, `GroupCommand`) with non-destructive Room v2 autosave (`ProjectDocumentMigrator`).
- `ScriptSceneSplitter` and its 18-word sentence/paragraph chunking rules remain intact in `VideoProjectModels.kt` for the upcoming Script-to-Video wizard.

## Verification & Reference Screenshots

Automated Robolectric Compose UI tests (`VideoEditorWorkspaceTest`) verify 360 dp phone portrait, phone landscape, and 10-inch tablet layouts, clip selection, long-press multi-selection, trim handle dragging, undo/redo, and save reference screenshots to:
- `docs/screens/phone_portrait.png`
- `docs/screens/phone_landscape.png`
- `docs/screens/tablet.png`
- `docs/screens/tablet_landscape.png`
