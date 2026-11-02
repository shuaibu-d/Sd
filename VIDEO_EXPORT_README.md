# SD Studio video exporter

## Implementation
- Pinned AndroidX Media3 **1.11.1** (`media3-transformer` + `media3-effect`).
- Exports are built with a video-only `EditedMediaItemSequence` and a synchronised audio-only sequence inside a `Composition`.
- Text scenes are rendered by the existing `TemplateRenderer` at the chosen output dimensions, saved as temporary PNG image items, and assigned their scene durations.
- Video-clip scenes use `MediaItem.ClippingConfiguration`; source clip audio is removed. Trim start/end are stored on `Scene` as `clipTrimStartMs` / `clipTrimEndMs`.
- Voice-over files are placed on the audio sequence at scene start times. `addGap()` supplies silence for scenes without a take and for the unused tail of shorter takes. The Media3 1.11.1 initial-gap audio-track requirement is handled with `experimentalSetForceAudioTrack(true)` only when the first scene begins with silence. If no usable take exists, a temporary silent WAV keeps the audio track present. A missing referenced file is reported after export; the original take is used if the normalised derivative is missing.
- The composition-level `OverlayEffect` uses a pre-scaled `BitmapOverlay`. Its size and top/right position are calculated from the shared `LogoPlacementRules` with `LogoPlacementMode.SAFE`. The selected variant defaults to FULL; ICON can be selected on the export screen.
- Transformer is created and accessed on a dedicated `HandlerThread` Looper. A foreground service reports progress, exposes a notification Cancel action, and saves the MP4 to `Movies/SD Studio` through MediaStore (or FileProvider on Android 8–9).
- Successful exports are inserted into the existing `ExportRecord` table. The export screen offers Share and View.

## Presets
| Preset | Main output | 720p fallback |
|---|---:|---:|
| Reels / TikTok / Shorts | 1080 × 1920 | 720 × 1280 |
| Feed | 1080 × 1350 | 720 × 900 |
| Square | 1080 × 1080 | 720 × 720 |
| YouTube | 1920 × 1080 | 1280 × 720 |

Image items are set to 30 fps and video clip items use `EditedMediaItem.Builder.setFrameRate(30)`. In Media3 1.11.1 this is a maximum for sources with an intrinsic frame rate; lower-frame-rate source clips are not upsampled because the constant-frame aggregation API was introduced after the pinned stable version. Output is H.264, AAC and SDR (HDR sources are tone-mapped to SDR using the Media3 OpenGL mode).

## Verify the logo position on a phone
1. In Brand, add both a FULL and ICON logo asset (or the variant you intend to test).
2. Create a short 9:16 project with two scenes and export once with FULL, then again with ICON.
3. Open each MP4 from Movies/SD Studio. Check the logo's visible right edge and top edge against the preview/editor safe placement. The 9:16 SAFE rule is 14% of canvas width, 6% right margin and 13% top margin. For Feed, Square and YouTube, use the corresponding values in `LogoPlacementRules`.
4. Repeat with 720p enabled. The logo should occupy the same percentage of the frame; the exporter scales both the bitmap and its placement for the fallback dimensions.
5. Inspect the first, middle and final frames. The composition-level overlay should remain at the same position throughout.

The overlay anchor is set to the top-right corner of the overlay bitmap, and the background anchor is computed from the top/right pixel edges returned by the shared placement rules. The current Media3 OverlaySettings NDC convention is applied when converting those pixel coordinates.

## Notes / limitations
- Transformer compositions do not currently provide crossfades between sequence items; scene transition metadata is not exported as a crossfade.
- A VIDEO_CLIP scene uses the clip as its visual item. Text template rendering is used for non-video scenes; a video background does not currently composite the scene's text on top of that clip.
- Media3 1.11.1's stable `setFrameRate(30)` caps high-frame-rate video sources but does not upsample a source below 30 fps. The later `VideoFrameAggregationParameters` API is not used because it is absent from the pinned 1.11.1 stable release.
- Export is run locally on the device; available hardware encoders and free storage vary. If a codec/encoder failure occurs, retry at 720p.
- The Android build and device export still need to be verified in the project's Gradle/Android SDK environment. Static Kotlin parser checks found no syntax diagnostics in the changed Kotlin files, but this environment does not have the Android SDK/Gradle wrapper needed for a full build.

For optional captions, the bottom teal progress line, and background music, see `VIDEO_EXPORT_POLISH_README.md`. These polish features are opt-in and disabled by default. Text entrance animations, cross-fades, and final-mix loudness normalization are intentionally disabled pending stable implementations; see the trade-offs in `ARCHITECTURE.md`.
