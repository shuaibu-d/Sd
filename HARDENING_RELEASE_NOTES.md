# SD Studio hardening and release preparation

## Included

- Settings: theme, default canvas ratio, SAFE/TIGHT logo placement, storage use, Clear Cache, Crash Log viewer, About/version.
- Theme applies from DataStore. New video and post editing sessions use saved ratio/logo defaults.
- Process-wide export lock prevents a second Transformer export while the foreground service is active. Returning to the export screen shows the lock and allows cancellation.
- Clear Cache is locked during export. Export checks estimated free space and offers a 720p fallback.
- New video scripts are limited to 40 scenes. Still images and logos are bounds-decoded and sampled. Scene bitmaps are recycled after writing. PSS/heap snapshots are logged under `SDStudioExportMemory`.
- Video script editor restores the open Room draft after activity recreation and limits very long input.
- Three unit tests cover placement geometry, script chunking, and duration calculation. UI teal shades were adjusted for stronger light/dark theme contrast, and image previews now expose descriptive accessibility labels.
- Release enables R8/resource shrinking with `app/proguard-rules.pro`; version is 2 / 1.1.0.
- `.github/workflows/release-aab.yml` builds a signed AAB from repository secrets.
- `RELEASE_FROM_PHONE.md`, `PRIVACY_POLICY.md`, and `PLAY_STORE_LISTING.md` are included.

## Verification checklist before publishing

1. Run `gradle testDebugUnitTest` in a complete Android/Gradle environment.
2. Run `gradle bundleRelease` with signing secrets and install/test the signed build on Android 8+.
3. Rotate editor/export screens; background/foreground the app; kill/relaunch while a draft is open.
4. Deny microphone permission, then retry recording after granting permission in Android Settings.
5. Move/delete imported images, clips, music, and voice files; verify recovery prompts rather than crashes.
6. Test low free storage, a 20,000-character script, many scenes, and large photos.
7. Start an export and try to start another; verify rejection. Reopen the screen and cancel from screen and notification.
8. Profile a 2–3 minute 1080p and 720p export in Android Studio Memory Profiler. Inspect `SDStudioExportMemory` and watch for steadily rising PSS.
9. Test R8 release behavior: Room, serialization, alarm receiver, foreground service, Media3 codecs, FileProvider sharing.
10. Review privacy policy, Play Data safety, support contact, icon, screenshots, and feature graphic.

## Known limitations

- No full Gradle build or device test was possible in the editing environment. Archive integrity and source inspection do not prove Android compilation/runtime stability.
- The archive has no Gradle wrapper. CI installs Gradle 9.7.0 directly; local builds require a compatible installed Gradle distribution (AGP 9.3.1, Kotlin 2.4.21).
- Export memory telemetry is coarse PSS/heap sampling, not a profiling trace. Device profiling remains required.
- Storage checks are heuristic and cannot guarantee enough space for every device/codec. Final MediaStore copy temporarily duplicates the output file.
- Video script editor uses saved instance state and Room draft restoration. Abrupt process kill inside the 650 ms autosave debounce can lose the latest keystrokes. Video export is not resumable after process death; an interrupted export must be started again. Other screens have not all been converted to full SavedStateHandle restoration.
- A complete manual audit of every screen and OEM-specific permission/codec behavior has not been performed. Existing empty states and guards were retained, but physical testing is still needed.
- Voice recording interruption recovery, URI permission revocation, and missing media file recovery require device testing.
- Settings controls use Material components with 48 dp minimum height, but a full TalkBack/accessibility contrast audit has not been certified.
- Crash Log stores the latest uncaught Java/Kotlin exception only; native crashes are not captured.
- Privacy policy and Play listing are drafts, not legal advice or final Play Console declarations.
