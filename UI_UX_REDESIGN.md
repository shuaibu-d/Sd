# SD Studio Pro — professional studio UI update

## Design intent

SD Studio keeps its established Midnight Navy, Innovation Teal, Premium Gold and Cloud White identity. The redesign uses a restrained production-software language: clear hierarchy, compact chrome, focused work surfaces, strong preview stages, predictable tool groups and fewer decorative cards. It is designed for a phone-first Android studio that can scale to larger screens without looking like a generic social app.

The interaction model was informed by current professional creator workflows:

- Adobe Premiere's workspace/panel organization and task-oriented editing layouts: https://helpx.adobe.com/premiere/desktop/get-started/tour-the-workspace/what-are-workspaces.html
- Adobe Premiere for Android's editing stage, transport controls, timeline and contextual tools: https://helpx.adobe.com/premiere/mobile/get-started/workspace-overview.html
- CapCut's import → preview → timeline → contextual clip editing workflow: https://www.capcut.com/resource/how-to-use-capcut
- Canva's media-first multi-track timeline, direct clip editing, visual audio waveforms and template-to-editor flow: https://www.canva.com/en_in/help/creating-and-editing-videos/

These references are workflow inspiration only. SD Studio retains its own palette, typography, assets and navigation.

## Changes implemented in source

### Global design system
- Tightened the shared screen header to a more compact 64dp studio toolbar with clearer title/subtitle hierarchy and a quieter divider.
- Standardized primary button geometry to a 12dp professional control shape.
- Refined general screen spacing and the shared bottom navigation height/selected icon treatment.
- Kept existing Material theme colours and typography; no new brand palette was introduced.

### Home workspace
- Rebuilt the home screen around a clear studio command area, primary Post and Video actions, tool/library shortcuts, template discovery and recent projects.
- Replaced consumer-style greeting copy with concise creative-production language.
- Reduced decorative surface stacking and gave the two primary creation actions visual priority.

### Video studio landing workspace
- Rebuilt the Video Studio hub around one production launch stage, concise project/workflow metrics and a focused production library.
- Preserved the existing create-project, open-project and delete-project callbacks.
- Removed the instructional-card feel and made the project list the primary operational area.

### Post editor and video inspector
- Increased the post editor's canvas stage to 350dp and kept the canvas on a deep-navy viewing surface.
- Replaced boxed text-entry controls in the post editor with unboxed inline text editing and a subtle divider. Text remains editable and connected to the existing editor ViewModel.
- Replaced the boxed scene-copy editor in the video inspector with an unboxed inline editor; existing scene update callbacks and word-count guidance remain connected.
- Restyled caption editing, Brand Kit text entry, planner title entry, voice-over duration entry and project renaming as unboxed studio copy controls with compact labels and dividers, rather than generic outlined form fields.
- Replaced the planner dialog’s outlined dropdown fields with text-button selectors; menus and selection callbacks remain intact.
- Removed Material `OutlinedTextField` controls from the app UI. Editable copy remains available through unboxed inline text controls; date/time and other discrete choices use dedicated controls.
- Kept the existing photo picker, text styling, slide controls, undo/redo, autosave and export actions wired to their existing logic.

### Template library and loading
- Refined the gallery card density and preview stage while retaining the existing brand theme, category/ratio filters and favourite behavior.
- Preserved asynchronous thumbnail rendering, bitmap-pool reuse, thermal/memory-aware thumbnail sizing and per-template retry handling.
- Fixed a repository cache failure mode: an empty template result is no longer cached permanently for the lifetime of the process. A subsequent load retries asset decoding.
- Added explicit repository cache invalidation to the gallery reload action.
- Kept the non-empty category fallback when no templates explicitly advertise a selected ratio, so the gallery does not appear empty unnecessarily.

## Preserved functionality

- Navigation routes and project persistence.
- Existing template JSON model and renderer.
- Brand kit, logo placement and image-slot handling.
- Video timeline, scene text callbacks, media handling and export pipeline.
- Autosave, undo/redo and existing project actions.

## Verification

- The 12 bundled template JSON assets are present in `app/src/main/assets/templates/`.
- Template asset decoding still uses the existing serialization model; renderer code and template definitions were not replaced.
- ZIP archive integrity and source-tree packaging are checked after generation.
- Android Gradle compilation could not be run in this environment: the Gradle wrapper requires Gradle 9.3.1 from `services.gradle.org`, which is unreachable here. This is an environment limitation, not a successful build result. Run the repository's GitHub Actions build or `./gradlew :app:assembleDebug` where the wrapper distribution and Android SDK are available before release.
- Device-level visual and interaction QA is still required on the target Android versions, including import permissions, large media, low-memory behavior and export completion.
