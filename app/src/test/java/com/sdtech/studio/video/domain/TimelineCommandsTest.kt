package com.sdtech.studio.video.domain

import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineCommandsTest {

    private fun sampleProject(): TimelineProject {
        val scenes = listOf(
            Scene(
                id = "scene_1",
                type = SceneType.INTRO,
                text = "Welcome to SD Studio",
                textStyle = SceneTextStyle.BIG_STATEMENT,
                backgroundType = SceneBackgroundType.BRAND_COLOR,
                manualDurationMs = 3_000L
            ),
            Scene(
                id = "scene_2",
                type = SceneType.CONTENT,
                text = "Build scroll stopping videos fast and easily",
                textStyle = SceneTextStyle.BULLET_POINT,
                backgroundType = SceneBackgroundType.GRADIENT,
                voiceFilePath = "/data/user/0/com.sdtech.studio/files/voiceovers/vo2.m4a",
                voiceDurationMs = 4_000L
            )
        )
        return ProjectDocumentMigrator.fromScenes(
            projectId = "proj_test",
            canvasRatio = "9:16",
            scenes = scenes
        )
    }

    @Test
    fun `01 default timeline project has version 2, 30 fps, and all 7 required track types`() {
        val project = TimelineProject(id = "p1", canvasRatio = "16:9")
        assertEquals(2, project.version)
        assertEquals(30, project.fps)
        assertEquals("16:9", project.canvasRatio)
        val types = project.tracks.map { it.type }.toSet()
        assertEquals(
            setOf(
                TrackType.VIDEO,
                TrackType.OVERLAY,
                TrackType.TEXT,
                TrackType.AUDIO_MUSIC,
                TrackType.AUDIO_VOICE,
                TrackType.AUDIO_SFX,
                TrackType.CAPTION
            ),
            types
        )
    }

    @Test
    fun `02 AddClipCommand adds clip to VIDEO track and reverts cleanly`() {
        val history = CommandHistory(TimelineProject(id = "p1"))
        val clip = Clip(
            id = "clip_v1",
            startMs = 500L,
            durationMs = 2_500L,
            sourceInMs = 100L,
            sourceOutMs = 2_600L,
            layerOrder = 1,
            sourceUri = "content://media/external/video/10"
        )
        val afterAdd = history.execute(AddClipCommand(clip = clip, targetTrackType = TrackType.VIDEO))
        val added = afterAdd.findClip("clip_v1")
        assertNotNull(added)
        assertEquals(500L, added!!.startMs)
        assertEquals(2_500L, added.durationMs)
        assertEquals("track_video", added.trackId)

        val afterUndo = history.undo()!!
        assertNull(afterUndo.findClip("clip_v1"))

        val afterRedo = history.redo()!!
        assertNotNull(afterRedo.findClip("clip_v1"))
    }

    @Test
    fun `03 AddClipCommand adds clips across OVERLAY, TEXT, AUDIO_MUSIC, AUDIO_VOICE, AUDIO_SFX, CAPTION tracks`() {
        val history = CommandHistory(TimelineProject(id = "p_tracks"))
        TrackType.entries.forEachIndexed { idx, type ->
            val clip = Clip(id = "c_${type.name}", startMs = idx * 1000L, durationMs = 1000L)
            history.execute(AddClipCommand(clip = clip, targetTrackType = type))
        }
        assertEquals(7, history.currentProject.clipCount)
        TrackType.entries.forEach { type ->
            val track = history.currentProject.findTrackByType(type)!!
            assertEquals(1, track.clips.size)
            assertEquals("c_${type.name}", track.clips.single().id)
        }
    }

    @Test
    fun `04 AddClipCommand forScene adds SceneBlock with background, text, and logo clips`() {
        val history = CommandHistory(sampleProject())
        val newScene = Scene(
            id = "scene_3",
            type = SceneType.OUTRO,
            text = "Follow us today",
            manualDurationMs = 2_000L
        )
        val updated = history.execute(AddClipCommand.forScene(newScene, insertIndex = 2))
        assertEquals(3, updated.sceneBlocks.size)
        val block = updated.findSceneBlock("scene_3")
        assertNotNull(block)
        assertNotNull(updated.findClip(block!!.backgroundClipId))
        assertNotNull(updated.findClip(block.textClipId))
        assertNotNull(updated.findClip(block.logoClipId))

        val undone = history.undo()!!
        assertEquals(2, undone.sceneBlocks.size)
        assertNull(undone.findSceneBlock("scene_3"))
    }

    @Test
    fun `05 MoveClipCommand moves grouped SceneBlock clips together when grouped`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            MoveClipCommand(
                clipId = "scene_1_bg",
                newStartMs = 1_200L,
                propagateToGroup = true
            )
        )
        assertEquals(1_200L, updated.findClip("scene_1_bg")!!.startMs)
        assertEquals(1_200L, updated.findClip("scene_1_text")!!.startMs)
        assertEquals(1_200L, updated.findClip("scene_1_logo")!!.startMs)

        val undone = history.undo()!!
        assertEquals(0L, undone.findClip("scene_1_bg")!!.startMs)
        assertEquals(0L, undone.findClip("scene_1_text")!!.startMs)
        assertEquals(0L, undone.findClip("scene_1_logo")!!.startMs)
    }

    @Test
    fun `06 MoveClipCommand moves single clip independently when propagateToGroup is false`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            MoveClipCommand(
                clipId = "scene_1_text",
                newStartMs = 800L,
                newLayerOrder = 5,
                propagateToGroup = false
            )
        )
        assertEquals(0L, updated.findClip("scene_1_bg")!!.startMs)
        assertEquals(800L, updated.findClip("scene_1_text")!!.startMs)
        assertEquals(5, updated.findClip("scene_1_text")!!.layerOrder)
        assertEquals(0L, updated.findClip("scene_1_logo")!!.startMs)
    }

    @Test
    fun `07 MoveClipCommand moves clip to a different track and reverts cleanly`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            MoveClipCommand(
                clipId = "scene_1_text",
                newStartMs = 500L,
                targetTrackId = "track_caption",
                propagateToGroup = false
            )
        )
        assertEquals("track_caption", updated.findTrackForClip("scene_1_text")?.id)
        val undone = history.undo()!!
        assertEquals("track_text", undone.findTrackForClip("scene_1_text")?.id)
    }

    @Test
    fun `08 MoveClipCommand reorderScene swaps scene order and recalculates startMs`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(MoveClipCommand.reorderScene("scene_1", 0, 1))
        val scenes = updated.toScenes()
        assertEquals(listOf("scene_2", "scene_1"), scenes.map { it.id })
        assertEquals(0L, updated.findClip("scene_2_bg")!!.startMs)
        assertEquals(4_400L, updated.findClip("scene_1_bg")!!.startMs)

        val undone = history.undo()!!
        assertEquals(listOf("scene_1", "scene_2"), undone.toScenes().map { it.id })
    }

    @Test
    fun `09 MoveClipCommand merges rapid slider drags within merge window into one command`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(MoveClipCommand(clipId = "scene_1_bg", newStartMs = 100L, isSliderDrag = true, timestampMs = 1_000L))
        history.execute(MoveClipCommand(clipId = "scene_1_bg", newStartMs = 250L, isSliderDrag = true, timestampMs = 1_120L))
        history.execute(MoveClipCommand(clipId = "scene_1_bg", newStartMs = 450L, isSliderDrag = true, timestampMs = 1_250L))

        assertEquals(1, history.undoCount)
        assertEquals(450L, history.currentProject.findClip("scene_1_bg")!!.startMs)

        val undone = history.undo()!!
        assertEquals(0L, undone.findClip("scene_1_bg")!!.startMs)
    }

    @Test
    fun `10 MoveClipCommand does not merge slider drags separated by more than merge window`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(MoveClipCommand(clipId = "scene_1_bg", newStartMs = 100L, isSliderDrag = true, timestampMs = 1_000L))
        history.execute(MoveClipCommand(clipId = "scene_1_bg", newStartMs = 400L, isSliderDrag = true, timestampMs = 1_800L))

        assertEquals(2, history.undoCount)
        val afterFirstUndo = history.undo()!!
        assertEquals(100L, afterFirstUndo.findClip("scene_1_bg")!!.startMs)
        val afterSecondUndo = history.undo()!!
        assertEquals(0L, afterSecondUndo.findClip("scene_1_bg")!!.startMs)
    }

    @Test
    fun `11 TrimClipCommand trims grouped SceneBlock clips and sets manual duration`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            TrimClipCommand(
                clipId = "scene_1_bg",
                newStartMs = 200L,
                newDurationMs = 5_000L,
                newSourceInMs = 500L,
                newSourceOutMs = 5_500L,
                setManualDuration = true,
                propagateToGroup = true
            )
        )
        val bg = updated.findClip("scene_1_bg")!!
        val txt = updated.findClip("scene_1_text")!!
        val logo = updated.findClip("scene_1_logo")!!
        assertEquals(200L, bg.startMs)
        assertEquals(5_000L, bg.durationMs)
        assertEquals(500L, bg.sourceInMs)
        assertEquals(5_500L, bg.sourceOutMs)
        assertTrue(bg.hasExplicitTrimEnd)
        assertEquals(5_000L, txt.durationMs)
        assertEquals(5_000L, logo.durationMs)

        val undone = history.undo()!!
        assertEquals(3_000L, undone.findClip("scene_1_bg")!!.durationMs)
        assertEquals(0L, undone.findClip("scene_1_bg")!!.startMs)
    }

    @Test
    fun `12 TrimClipCommand merges rapid trim handle drags into single undo step`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(TrimClipCommand(clipId = "scene_1_bg", newDurationMs = 3_200L, isSliderDrag = true, timestampMs = 2_000L))
        history.execute(TrimClipCommand(clipId = "scene_1_bg", newDurationMs = 3_600L, isSliderDrag = true, timestampMs = 2_100L))
        history.execute(TrimClipCommand(clipId = "scene_1_bg", newDurationMs = 4_200L, isSliderDrag = true, timestampMs = 2_250L))

        assertEquals(1, history.undoCount)
        assertEquals(4_200L, history.currentProject.findClip("scene_1_bg")!!.durationMs)

        val undone = history.undo()!!
        assertEquals(3_000L, undone.findClip("scene_1_bg")!!.durationMs)
    }

    @Test
    fun `13 TrimClipCommand enforces minimum 1ms duration`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            TrimClipCommand(clipId = "scene_1_bg", newDurationMs = -50L)
        )
        assertEquals(1L, updated.findClip("scene_1_bg")!!.durationMs)
    }

    @Test
    fun `14 SplitClipCommand splits an ungrouped clip into two contiguous clips and partitions keyframes`() {
        val base = TimelineProject(id = "p_split")
        val history = CommandHistory(base)
        val kf1 = Keyframe(id = "k1", timeOffsetMs = 500L, property = KeyframeProperty.OPACITY, value = 0.5f)
        val kf2 = Keyframe(id = "k2", timeOffsetMs = 2_500L, property = KeyframeProperty.OPACITY, value = 1.0f)
        val clip = Clip(
            id = "raw_clip",
            startMs = 1_000L,
            durationMs = 4_000L,
            sourceInMs = 200L,
            sourceOutMs = 4_200L,
            keyframes = listOf(kf1, kf2)
        )
        history.execute(AddClipCommand(clip = clip, targetTrackType = TrackType.VIDEO))

        val afterSplit = history.execute(
            SplitClipCommand(
                clipId = "raw_clip",
                splitOffsetMs = 1_500L,
                leftClipId = "raw_left",
                rightClipId = "raw_right",
                splitGroup = false
            )
        )
        assertNull(afterSplit.findClip("raw_clip"))
        val left = afterSplit.findClip("raw_left")!!
        val right = afterSplit.findClip("raw_right")!!
        assertEquals(1_000L, left.startMs)
        assertEquals(1_500L, left.durationMs)
        assertEquals(200L, left.sourceInMs)
        assertEquals(1_700L, left.sourceOutMs)
        assertEquals(1, left.keyframes.size)
        assertEquals(500L, left.keyframes.single().timeOffsetMs)

        assertEquals(2_500L, right.startMs)
        assertEquals(2_500L, right.durationMs)
        assertEquals(1_700L, right.sourceInMs)
        assertEquals(4_200L, right.sourceOutMs)
        assertEquals(1, right.keyframes.size)
        assertEquals(1_000L, right.keyframes.single().timeOffsetMs)

        val afterUndo = history.undo()!!
        assertNotNull(afterUndo.findClip("raw_clip"))
        assertNull(afterUndo.findClip("raw_left"))
        assertNull(afterUndo.findClip("raw_right"))
    }

    @Test
    fun `15 SplitClipCommand splits grouped SceneBlock into two SceneBlocks and reverts cleanly`() {
        val history = CommandHistory(sampleProject())
        val afterSplit = history.execute(
            SplitClipCommand(
                clipId = "scene_1_bg",
                splitOffsetMs = 1_200L,
                splitGroup = true
            )
        )
        assertEquals(3, afterSplit.sceneBlocks.size)
        assertEquals("scene_1_part1", afterSplit.sceneBlocks[0].id)
        assertEquals("scene_1_part2", afterSplit.sceneBlocks[1].id)
        assertEquals("Welcome to", afterSplit.findClip(afterSplit.sceneBlocks[0].textClipId)?.text)
        assertEquals("SD Studio", afterSplit.findClip(afterSplit.sceneBlocks[1].textClipId)?.text)

        val undone = history.undo()!!
        assertEquals(2, undone.sceneBlocks.size)
        assertEquals("scene_1", undone.sceneBlocks[0].id)
        assertEquals("Welcome to SD Studio", undone.findClip("scene_1_text")?.text)
    }

    @Test
    fun `16 SplitClipCommand ignores invalid split offset at 0 or beyond duration`() {
        val history = CommandHistory(sampleProject())
        val unchangedZero = history.execute(SplitClipCommand(clipId = "scene_1_bg", splitOffsetMs = 0L))
        assertEquals(2, unchangedZero.sceneBlocks.size)
        val unchangedPastEnd = history.execute(SplitClipCommand(clipId = "scene_1_bg", splitOffsetMs = 9_999L))
        assertEquals(2, unchangedPastEnd.sceneBlocks.size)
    }

    @Test
    fun `17 DeleteClipCommand deletes entire grouped SceneBlock and restores on undo`() {
        val history = CommandHistory(sampleProject())
        val afterDelete = history.execute(DeleteClipCommand(clipId = "scene_1_bg", deleteGroup = true))
        assertEquals(1, afterDelete.sceneBlocks.size)
        assertNull(afterDelete.findClip("scene_1_bg"))
        assertNull(afterDelete.findClip("scene_1_text"))
        assertNull(afterDelete.findClip("scene_1_logo"))

        val afterUndo = history.undo()!!
        assertEquals(2, afterUndo.sceneBlocks.size)
        assertNotNull(afterUndo.findClip("scene_1_bg"))
        assertNotNull(afterUndo.findClip("scene_1_text"))
        assertNotNull(afterUndo.findClip("scene_1_logo"))
    }

    @Test
    fun `18 DeleteClipCommand deletes only single clip when deleteGroup is false`() {
        val history = CommandHistory(sampleProject())
        val afterDelete = history.execute(DeleteClipCommand(clipId = "scene_1_logo", deleteGroup = false))
        assertNull(afterDelete.findClip("scene_1_logo"))
        assertNotNull(afterDelete.findClip("scene_1_bg"))
        assertNotNull(afterDelete.findClip("scene_1_text"))

        val afterUndo = history.undo()!!
        assertNotNull(afterUndo.findClip("scene_1_logo"))
    }

    @Test
    fun `19 SetPropertyCommand updates transform x, y, scale, rotation, opacity and reverts`() {
        val history = CommandHistory(sampleProject())
        val customTransform = ClipTransform(x = 0.25f, y = -0.15f, scale = 1.4f, rotation = 15f, opacity = 0.8f)
        val updated = history.execute(SetPropertyCommand.transform("scene_1_text", customTransform))
        assertEquals(customTransform, updated.findClip("scene_1_text")!!.transform)

        val undone = history.undo()!!
        assertEquals(ClipTransform(), undone.findClip("scene_1_text")!!.transform)
    }

    @Test
    fun `20 SetPropertyCommand scale merges rapid slider drags into one command`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.scale("scene_1_text", 1.1f, isSliderDrag = true, timestampMs = 1_000L))
        history.execute(SetPropertyCommand.scale("scene_1_text", 1.25f, isSliderDrag = true, timestampMs = 1_080L))
        history.execute(SetPropertyCommand.scale("scene_1_text", 1.5f, isSliderDrag = true, timestampMs = 1_160L))

        assertEquals(1, history.undoCount)
        assertEquals(1.5f, history.currentProject.findClip("scene_1_text")!!.transform.scale, 0.0001f)

        val undone = history.undo()!!
        assertEquals(1.0f, undone.findClip("scene_1_text")!!.transform.scale, 0.0001f)
    }

    @Test
    fun `21 SetPropertyCommand opacity merges rapid slider drags and clamps to 0 to 1`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.opacity("scene_1_logo", 0.9f, isSliderDrag = true, timestampMs = 5_000L))
        history.execute(SetPropertyCommand.opacity("scene_1_logo", 0.4f, isSliderDrag = true, timestampMs = 5_100L))
        history.execute(SetPropertyCommand.opacity("scene_1_logo", -0.5f, isSliderDrag = true, timestampMs = 5_200L))

        assertEquals(1, history.undoCount)
        assertEquals(0.0f, history.currentProject.findClip("scene_1_logo")!!.transform.opacity, 0.0001f)

        val undone = history.undo()!!
        assertEquals(1.0f, undone.findClip("scene_1_logo")!!.transform.opacity, 0.0001f)
    }

    @Test
    fun `22 SetPropertyCommand position merges rapid slider drags`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.position("scene_1_logo", 0.1f, 0.2f, isSliderDrag = true, timestampMs = 100L))
        history.execute(SetPropertyCommand.position("scene_1_logo", 0.5f, 0.6f, isSliderDrag = true, timestampMs = 200L))

        assertEquals(1, history.undoCount)
        assertEquals(0.5f, history.currentProject.findClip("scene_1_logo")!!.transform.x, 0.0001f)
        assertEquals(0.6f, history.currentProject.findClip("scene_1_logo")!!.transform.y, 0.0001f)
    }

    @Test
    fun `23 SetPropertyCommand rotation merges rapid slider drags`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.rotation("scene_1_logo", 10f, isSliderDrag = true, timestampMs = 100L))
        history.execute(SetPropertyCommand.rotation("scene_1_logo", 45f, isSliderDrag = true, timestampMs = 220L))

        assertEquals(1, history.undoCount)
        assertEquals(45f, history.currentProject.findClip("scene_1_logo")!!.transform.rotation, 0.0001f)
        val undone = history.undo()!!
        assertEquals(0f, undone.findClip("scene_1_logo")!!.transform.rotation, 0.0001f)
    }

    @Test
    fun `24 SetPropertyCommand volume merges rapid slider drags`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.volume("scene_2_voice", 0.8f, isSliderDrag = true, timestampMs = 100L))
        history.execute(SetPropertyCommand.volume("scene_2_voice", 0.35f, isSliderDrag = true, timestampMs = 250L))

        assertEquals(1, history.undoCount)
        assertEquals(0.35f, history.currentProject.findClip("scene_2_voice")!!.volume, 0.0001f)
        val undone = history.undo()!!
        assertEquals(1.0f, undone.findClip("scene_2_voice")!!.volume, 0.0001f)
    }

    @Test
    fun `25 SetPropertyCommand does not merge different properties on same clip`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.scale("scene_1_logo", 1.2f, isSliderDrag = true, timestampMs = 100L))
        history.execute(SetPropertyCommand.opacity("scene_1_logo", 0.5f, isSliderDrag = true, timestampMs = 150L))

        assertEquals(2, history.undoCount)
    }

    @Test
    fun `26 SetPropertyCommand does not merge across commitSliderGesture barrier`() {
        val history = CommandHistory(sampleProject(), mergeWindowMs = 500L)
        history.execute(SetPropertyCommand.scale("scene_1_logo", 1.2f, isSliderDrag = true, timestampMs = 100L))
        history.commitSliderGesture()
        history.execute(SetPropertyCommand.scale("scene_1_logo", 1.5f, isSliderDrag = true, timestampMs = 150L))

        assertEquals(2, history.undoCount)
        val afterFirstUndo = history.undo()!!
        assertEquals(1.2f, afterFirstUndo.findClip("scene_1_logo")!!.transform.scale, 0.0001f)
    }

    @Test
    fun `27 SetPropertyCommand updates text and textStyle and templateId`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            SetPropertyCommand.text(
                clipId = "scene_1_text",
                newText = "Updated bullet text",
                newTextStyle = SceneTextStyle.BULLET_POINT
            )
        )
        val clip = updated.findClip("scene_1_text")!!
        assertEquals("Updated bullet text", clip.text)
        assertEquals(SceneTextStyle.BULLET_POINT, clip.textStyle)
        assertEquals("scene_bullet", clip.templateId)
    }

    @Test
    fun `28 SetPropertyCommand updates backgroundType and sourceUri`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            SetPropertyCommand.background(
                clipId = "scene_1_bg",
                backgroundType = SceneBackgroundType.IMAGE,
                sourceUri = "content://media/picker/image_42",
                colorHex = "#101C2C"
            )
        )
        val bg = updated.findClip("scene_1_bg")!!
        assertEquals(SceneBackgroundType.IMAGE, bg.backgroundType)
        assertEquals("content://media/picker/image_42", bg.sourceUri)
        assertEquals("#101C2C", bg.colorHex)
    }

    @Test
    fun `29 SetPropertyCommand updates effects list and reverts`() {
        val history = CommandHistory(sampleProject())
        val effects = listOf(
            ClipEffect(id = "fx1", type = "BLUR", intensity = 0.4f, params = mapOf("radius" to "12")),
            ClipEffect(id = "fx2", type = "VIGNETTE", intensity = 0.7f)
        )
        val updated = history.execute(SetPropertyCommand.effects("scene_1_bg", effects))
        assertEquals(2, updated.findClip("scene_1_bg")!!.effects.size)
        assertEquals("BLUR", updated.findClip("scene_1_bg")!!.effects[0].type)

        val undone = history.undo()!!
        assertTrue(undone.findClip("scene_1_bg")!!.effects.isEmpty())
    }

    @Test
    fun `30 SetPropertyCommand updates keyframes list and reverts`() {
        val history = CommandHistory(sampleProject())
        val keyframes = listOf(
            Keyframe(id = "kf_1", timeOffsetMs = 0L, property = KeyframeProperty.SCALE, value = 1.0f, easing = EasingCurve.EASE_IN_OUT),
            Keyframe(id = "kf_2", timeOffsetMs = 1_500L, property = KeyframeProperty.SCALE, value = 1.25f, easing = EasingCurve.EASE_OUT)
        )
        val updated = history.execute(SetPropertyCommand.keyframes("scene_1_bg", keyframes))
        assertEquals(2, updated.findClip("scene_1_bg")!!.keyframes.size)

        val undone = history.undo()!!
        assertTrue(undone.findClip("scene_1_bg")!!.keyframes.isEmpty())
    }

    @Test
    fun `31 SetPropertyCommand updates animation presets in, out, and loop`() {
        val history = CommandHistory(sampleProject())
        val presets = AnimationPresets(
            inPreset = AnimationPreset.SLIDE_UP,
            outPreset = AnimationPreset.FADE_OUT,
            loopPreset = AnimationPreset.PULSE_LOOP
        )
        val updated = history.execute(SetPropertyCommand.animations("scene_1_text", presets))
        assertEquals(presets, updated.findClip("scene_1_text")!!.animations)

        val undone = history.undo()!!
        assertEquals(AnimationPreset.FADE_IN, undone.findClip("scene_1_text")!!.animations.inPreset)
    }

    @Test
    fun `32 SetPropertyCommand updates layerOrder and reverts`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(SetPropertyCommand.layerOrder("scene_1_logo", 99))
        assertEquals(99, updated.findClip("scene_1_logo")!!.layerOrder)
        val undone = history.undo()!!
        assertEquals(2, undone.findClip("scene_1_logo")!!.layerOrder)
    }

    @Test
    fun `33 SetPropertyCommand updates canvasRatio on project and reverts`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(SetPropertyCommand.canvasRatio("1:1"))
        assertEquals("1:1", updated.canvasRatio)
        val undone = history.undo()!!
        assertEquals("9:16", undone.canvasRatio)
    }

    @Test
    fun `34 GroupCommand ungroups SceneBlock so clips move independently, then reverts`() {
        val history = CommandHistory(sampleProject())
        val ungrouped = history.execute(GroupCommand.ungroup("scene_1"))
        assertFalse(ungrouped.findSceneBlock("scene_1")!!.isGrouped)
        assertNull(ungrouped.findClip("scene_1_bg")!!.groupId)
        assertNull(ungrouped.findClip("scene_1_text")!!.groupId)
        assertNull(ungrouped.findClip("scene_1_logo")!!.groupId)

        // Now moving scene_1_text does NOT move scene_1_bg even with propagateToGroup = true
        val movedTextOnly = history.execute(MoveClipCommand(clipId = "scene_1_text", newStartMs = 900L, propagateToGroup = true))
        assertEquals(0L, movedTextOnly.findClip("scene_1_bg")!!.startMs)
        assertEquals(900L, movedTextOnly.findClip("scene_1_text")!!.startMs)

        // Undo move and undo ungroup
        history.undo()
        val regroupedByUndo = history.undo()!!
        assertTrue(regroupedByUndo.findSceneBlock("scene_1")!!.isGrouped)
        assertEquals("scene_1", regroupedByUndo.findClip("scene_1_bg")!!.groupId)
    }

    @Test
    fun `35 GroupCommand regroups ungrouped clips back into a unit`() {
        val history = CommandHistory(sampleProject())
        history.execute(GroupCommand.ungroup("scene_1"))
        val regrouped = history.execute(
            GroupCommand.group("scene_1", listOf("scene_1_bg", "scene_1_text", "scene_1_logo"))
        )
        assertTrue(regrouped.findSceneBlock("scene_1")!!.isGrouped)
        assertEquals("scene_1", regrouped.findClip("scene_1_bg")!!.groupId)
        assertEquals("scene_1", regrouped.findClip("scene_1_text")!!.groupId)
        assertEquals("scene_1", regrouped.findClip("scene_1_logo")!!.groupId)
    }

    @Test
    fun `36 GroupCommand creates a new custom group from arbitrary clips across tracks`() {
        val history = CommandHistory(TimelineProject(id = "p_group"))
        history.execute(AddClipCommand(clip = Clip(id = "v_a", startMs = 0L, durationMs = 2000L), targetTrackType = TrackType.VIDEO))
        history.execute(AddClipCommand(clip = Clip(id = "t_a", startMs = 0L, durationMs = 2000L, text = "Hello"), targetTrackType = TrackType.TEXT))

        val grouped = history.execute(GroupCommand.group("custom_grp", listOf("v_a", "t_a")))
        assertEquals("custom_grp", grouped.findClip("v_a")!!.groupId)
        assertEquals("custom_grp", grouped.findClip("t_a")!!.groupId)
        assertNotNull(grouped.findSceneBlock("custom_grp"))
    }

    @Test
    fun `37 CommandHistory enforces max depth 100 and evicts oldest commands beyond 100`() {
        val history = CommandHistory(sampleProject(), maxDepth = 100)
        for (step in 1..125) {
            history.execute(
                SetPropertyCommand.layerOrder(
                    clipId = "scene_1_logo",
                    newLayerOrder = step,
                    timestampMs = step * 1000L
                )
            )
        }
        assertEquals(100, history.undoCount)
        assertEquals(125, history.currentProject.findClip("scene_1_logo")!!.layerOrder)

        // Undo all 100 retained commands
        repeat(100) {
            assertNotNull(history.undo())
        }
        assertFalse(history.canUndo)
        assertEquals(100, history.redoCount)
        // Because the first 25 commands were evicted, undoing 100 steps lands at step 25
        assertEquals(25, history.currentProject.findClip("scene_1_logo")!!.layerOrder)

        // Redo all 100 steps back to 125
        repeat(100) {
            assertNotNull(history.redo())
        }
        assertFalse(history.canRedo)
        assertEquals(125, history.currentProject.findClip("scene_1_logo")!!.layerOrder)
    }

    @Test
    fun `38 CommandHistory clears redo stack when a new command is executed after undo`() {
        val history = CommandHistory(sampleProject())
        history.execute(SetPropertyCommand.scale("scene_1_logo", 1.5f, isSliderDrag = false))
        history.execute(SetPropertyCommand.scale("scene_1_logo", 2.0f, isSliderDrag = false))
        history.undo()
        assertTrue(history.canRedo)

        history.execute(SetPropertyCommand.opacity("scene_1_logo", 0.7f, isSliderDrag = false))
        assertFalse(history.canRedo)
        assertEquals(0, history.redoCount)
    }

    @Test
    fun `39 CommandHistory undo and redo return null when stacks are empty`() {
        val history = CommandHistory(sampleProject())
        assertNull(history.undo())
        assertNull(history.redo())
    }

    @Test
    fun `40 SetPropertyCommand updateScene preserves visual output of scene templates`() {
        val history = CommandHistory(sampleProject())
        val updated = history.execute(
            SetPropertyCommand.updateScene("scene_2", "textStyle") {
                it.copy(textStyle = SceneTextStyle.HIGHLIGHTED_KEYWORD, text = "Power up your brand")
            }
        )
        val scene2 = updated.toScenes().first { it.id == "scene_2" }
        assertEquals(SceneTextStyle.HIGHLIGHTED_KEYWORD, scene2.textStyle)
        assertEquals("Power up your brand", scene2.text)
        assertEquals("scene_statement", updated.findClip("scene_2_text")!!.templateId)
    }

    @Test
    fun `41 SetPropertyCommand replaceAllScenes replaces scenes and supports undo`() {
        val history = CommandHistory(sampleProject())
        val newScenes = listOf(
            Scene(id = "s_new_1", type = SceneType.INTRO, text = "Fresh intro"),
            Scene(id = "s_new_2", type = SceneType.CONTENT, text = "Fresh body"),
            Scene(id = "s_new_3", type = SceneType.OUTRO, text = "Fresh outro")
        )
        val replaced = history.execute(SetPropertyCommand.replaceAllScenes(newScenes))
        assertEquals(3, replaced.toScenes().size)
        assertEquals("Fresh body", replaced.toScenes()[1].text)

        val undone = history.undo()!!
        assertEquals(2, undone.toScenes().size)
        assertEquals("Welcome to SD Studio", undone.toScenes()[0].text)
    }
}
