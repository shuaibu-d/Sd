# Quick Preview Player

The explainer-video editor now opens a local Quick Preview screen before export.

## Playback
- Loads the saved `VIDEO` project payload from Room.
- Renders each scene through the same `TemplateRenderer` and scene-template assets used by the video editor.
- Uses 540 px-wide preview bitmaps, preserving the selected canvas ratio.
- Applies `LogoPlacementMode.SAFE`, which uses the shared `LogoPlacementRules` also used by export. Safe-zone guide lines are shown as preview guides; they are not baked into exported artwork.
- Plays each scene's active voice take (`voiceFilePath`, which points at the normalized take when normalization has been applied) with Android `MediaPlayer`.
- Uses manual scene duration when supplied; otherwise uses `voiceDurationMs + 400 ms`. Scenes without a voice duration use a 3-second preview fallback until a take is recorded.
- Stops audio and pauses the timeline when the app leaves the foreground.

## Timeline controls
- Tap a scene block to jump to its start.
- Scrub across the complete timeline; while scrubbing, playback pauses and resumes from the selected position if it was playing before the gesture.
- Play/pause, Previous and Next controls are available.
- A warning appears when the estimated total duration exceeds 60 seconds.

## Memory and limitations
- Scene preview bitmaps are held in memory only for the preview screen and recycled when replaced or when leaving the screen.
- This is a timing-oriented preview, not a frame-accurate video renderer. Scene changes are timer-driven and audio is started asynchronously by `MediaPlayer`; device scheduling can introduce small timing differences.
- Video clip backgrounds use a still frame at the beginning of the clip, consistent with the current scene-editor thumbnail behavior. The preview does not play background video clips or render transitions.
- The preview is local-only and does not create or export a video file.
