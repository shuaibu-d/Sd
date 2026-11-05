package com.sdtech.studio.ui.video

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.sdtech.studio.ui.screens.VideoScriptEditorScreen
import com.sdtech.studio.ui.theme.SDStudioTheme
import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.domain.ProjectDocumentMigrator
import com.sdtech.studio.video.domain.TimelineProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoEditorWorkspaceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun createSampleTimelineProject(): TimelineProject {
        return ProjectDocumentMigrator.fromScenes(
            projectId = "test_video_workspace",
            canvasRatio = "9:16",
            scenes = listOf(
                Scene(
                    id = "scene_intro",
                    type = SceneType.INTRO,
                    text = "SD Technologies: Enterprise Cloud & AI\nZero-trust architecture built for scale."
                ),
                Scene(
                    id = "scene_outro",
                    type = SceneType.OUTRO,
                    text = "Partner With SD Technologies\nsdtechnologies.com"
                )
            )
        )
    }

    private fun resolveScreenshotsDir(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val rootDir = if (cwd.name == "app") cwd.parentFile ?: cwd else cwd
        val screensDir = File(rootDir, "docs/screens")
        if (!screensDir.exists()) {
            screensDir.mkdirs()
        }
        return screensDir
    }

    private fun saveScreenshot(fileName: String) {
        composeRule.waitForIdle()
        val decorView = composeRule.activity.window.decorView
        val width = decorView.width.coerceAtLeast(360)
        val height = decorView.height.coerceAtLeast(360)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decorView.draw(canvas)

        val outDir = resolveScreenshotsDir()
        val outFile = File(outDir, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Screenshot $fileName should exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-port")
    fun phonePortrait360dp_supportsSelectionMultiSelectionTrimAndBottomSheets_andSavesScreenshot() {
        val initialProject = createSampleTimelineProject()
        var latestProject = initialProject
        var latestSelection = emptySet<String>()

        composeRule.setContent {
            SDStudioTheme(darkTheme = true) {
                VideoScriptEditorScreen(
                    projectIdArg = initialProject.id,
                    initialProjectForTest = initialProject,
                    onProjectChangedForTest = { project, selectedIds ->
                        latestProject = project
                        latestSelection = selectedIds
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Verify 360dp portrait layout regions exist
        composeRule.onNodeWithTag("portrait_studio_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("video_editor_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("video_canvas_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("canvas_timeline_split_handle").assertIsDisplayed()
        composeRule.onNodeWithTag("video_transport_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_track_timeline").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_context_toolbar").assertIsDisplayed()

        // Save phone portrait screenshot
        saveScreenshot("phone_portrait.png")

        // 2. Tap second scene clip to select
        composeRule.onNodeWithTag("timeline_clip_scene_outro_bg").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("scene_outro_bg"), latestSelection)

        // 3. Long-press first scene clip to multi-select
        composeRule.onNodeWithTag("timeline_clip_scene_intro_bg").performTouchInput {
            longClick()
        }
        composeRule.waitForIdle()
        assertEquals(setOf("scene_outro_bg", "scene_intro_bg"), latestSelection)

        // 4. Tap first scene clip to single-select before trimming
        composeRule.onNodeWithTag("timeline_clip_scene_intro_bg").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("scene_intro_bg"), latestSelection)

        val beforeTrimDuration = latestProject.tracks
            .flatMap { it.clips }
            .first { it.id == "scene_intro_bg" }
            .durationMs
        assertTrue("Initial clip duration should be positive", beforeTrimDuration >= 2_000L)

        // 5. Drag right trim handle to extend clip duration
        composeRule.onNodeWithTag("trim_handle_right_scene_intro_bg").performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 90f, durationMillis = 200L)
        }
        composeRule.waitForIdle()

        val afterTrimDuration = latestProject.tracks
            .flatMap { it.clips }
            .first { it.id == "scene_intro_bg" }
            .durationMs
        assertNotEquals("Dragging right trim handle should change clip duration", beforeTrimDuration, afterTrimDuration)
        assertTrue("Dragging right trim handle rightwards should increase duration", afterTrimDuration > beforeTrimDuration)

        // 6. Verify Undo & Redo work on trim command
        composeRule.onNodeWithTag("video_editor_undo_button").performClick()
        composeRule.waitForIdle()
        val undoneDuration = latestProject.tracks
            .flatMap { it.clips }
            .first { it.id == "scene_intro_bg" }
            .durationMs
        assertEquals(beforeTrimDuration, undoneDuration)

        composeRule.onNodeWithTag("video_editor_redo_button").performClick()
        composeRule.waitForIdle()
        val redoneDuration = latestProject.tracks
            .flatMap { it.clips }
            .first { it.id == "scene_intro_bg" }
            .durationMs
        assertEquals(afterTrimDuration, redoneDuration)

        // 7. Open Text bottom sheet and verify it never covers the canvas preview
        composeRule.onNodeWithTag("toolbar_tab_text").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_bottom_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("video_canvas_preview").assertIsDisplayed()

        val canvasBounds = composeRule.onNodeWithTag("video_canvas_preview").fetchSemanticsNode().boundsInRoot
        val sheetBounds = composeRule.onNodeWithTag("studio_bottom_sheet").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Bottom sheet top (${sheetBounds.top}) must not cover canvas preview (canvas top=${canvasBounds.top}, bottom=${canvasBounds.bottom})",
            sheetBounds.top >= canvasBounds.bottom - 2f
        )

        // Close bottom sheet
        composeRule.onNodeWithTag("close_bottom_sheet_button").performClick()
        composeRule.waitForIdle()

        // 8. Verify Immersive Mode toggle hides top bar and shows full preview
        composeRule.onNodeWithTag("transport_immersive_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("exit_immersive_button").assertIsDisplayed()
        composeRule.onNodeWithTag("exit_immersive_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("video_editor_top_bar").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-land")
    fun phoneLandscape_rendersStudioDockLayout_andSavesScreenshot() {
        val initialProject = createSampleTimelineProject()

        composeRule.setContent {
            SDStudioTheme(darkTheme = true) {
                VideoScriptEditorScreen(
                    projectIdArg = initialProject.id,
                    initialProjectForTest = initialProject
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("landscape_tablet_studio_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("video_canvas_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_inspector_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_track_timeline").assertIsDisplayed()

        saveScreenshot("phone_landscape.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun tablet10Inch_rendersCanvasLeftInspectorRightTimelineBottom_andSupportsSelectionAndTrim() {
        val initialProject = createSampleTimelineProject()
        var latestProject = initialProject
        var latestSelection = emptySet<String>()

        composeRule.setContent {
            SDStudioTheme(darkTheme = true) {
                VideoScriptEditorScreen(
                    projectIdArg = initialProject.id,
                    initialProjectForTest = initialProject,
                    onProjectChangedForTest = { project, selectedIds ->
                        latestProject = project
                        latestSelection = selectedIds
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Verify canonical tablet layout: Canvas left, Inspector right, Timeline across bottom
        composeRule.onNodeWithTag("landscape_tablet_studio_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("video_canvas_preview").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_inspector_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_track_timeline").assertIsDisplayed()

        val canvasBounds = composeRule.onNodeWithTag("video_canvas_preview").fetchSemanticsNode().boundsInRoot
        val inspectorBounds = composeRule.onNodeWithTag("studio_inspector_panel").fetchSemanticsNode().boundsInRoot
        val timelineBounds = composeRule.onNodeWithTag("multi_track_timeline").fetchSemanticsNode().boundsInRoot

        assertTrue("Canvas should be to the left of Inspector on tablet", canvasBounds.left < inspectorBounds.left)
        assertTrue("Timeline should be below Canvas on tablet", timelineBounds.top >= canvasBounds.bottom - 2f)

        // Select second clip and trim via inspector trim +0.5s button
        composeRule.onNodeWithTag("timeline_clip_scene_outro_bg").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("scene_outro_bg"), latestSelection)

        val initialOutroDuration = latestProject.tracks
            .flatMap { it.clips }
            .first { it.id == "scene_outro_bg" }
            .durationMs

        composeRule.onNodeWithTag("inspector_trim_plus_button").performClick()
        composeRule.waitForIdle()

        val updatedOutroDuration = latestProject.tracks
            .flatMap { it.clips }
            .first { it.id == "scene_outro_bg" }
            .durationMs
        assertEquals(initialOutroDuration + 500L, updatedOutroDuration)

        saveScreenshot("tablet.png")
        saveScreenshot("tablet_landscape.png")
    }
}
