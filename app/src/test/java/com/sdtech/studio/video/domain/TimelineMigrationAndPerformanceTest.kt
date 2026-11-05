package com.sdtech.studio.video.domain

import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.VideoDurationCalculator
import com.sdtech.studio.video.VideoProjectPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMigrationAndPerformanceTest {

    private val realV1SampleJson = """
        {
          "version": 1,
          "scenes": [
            {
              "id": "scene-intro-01",
              "type": "INTRO",
              "text": "Your brand story starts here.\nIdeas that move people.",
              "textStyle": "BIG_STATEMENT",
              "backgroundType": "BRAND_COLOR",
              "backgroundUri": null,
              "clipTrimStartMs": 0,
              "clipTrimEndMs": null,
              "voiceFilePath": "/data/user/0/com.sdtech.studio/files/voiceovers/p1/intro.m4a",
              "voiceOriginalFilePath": "/data/user/0/com.sdtech.studio/files/voiceovers/p1/intro_raw.m4a",
              "normalizedLufs": -16.1,
              "normalizedTruePeakDbtp": -1.2,
              "normalizationGainDb": 3.4,
              "voiceDurationMs": 2800,
              "waveformLevels": [0.15, 0.45, 0.82, 0.61, 0.25],
              "manualDurationMs": null,
              "transition": "FADE"
            },
            {
              "id": "scene-content-02",
              "type": "CONTENT",
              "text": "Automate repetitive workflows with reliable software tailored to your team.",
              "textStyle": "BULLET_POINT",
              "backgroundType": "VIDEO_CLIP",
              "backgroundUri": "content://com.android.providers.media.documents/document/video%3A9912",
              "clipTrimStartMs": 1200,
              "clipTrimEndMs": 6200,
              "voiceFilePath": "/data/user/0/com.sdtech.studio/files/voiceovers/p1/content2.m4a",
              "voiceOriginalFilePath": null,
              "normalizedLufs": null,
              "normalizedTruePeakDbtp": null,
              "normalizationGainDb": null,
              "voiceDurationMs": 4100,
              "waveformLevels": [0.3, 0.7, 0.5],
              "manualDurationMs": 5000,
              "transition": "NONE"
            },
            {
              "id": "scene-outro-03",
              "type": "OUTRO",
              "text": "Follow for more.\nVisit our website or message us on WhatsApp.",
              "textStyle": "HIGHLIGHTED_KEYWORD",
              "backgroundType": "IMAGE",
              "backgroundUri": "content://com.android.providers.media.documents/document/image%3A401",
              "clipTrimStartMs": 0,
              "clipTrimEndMs": null,
              "voiceFilePath": null,
              "voiceOriginalFilePath": null,
              "normalizedLufs": null,
              "normalizedTruePeakDbtp": null,
              "normalizationGainDb": null,
              "voiceDurationMs": null,
              "waveformLevels": [],
              "manualDurationMs": null,
              "transition": "FADE"
            }
          ],
          "showIntro": true,
          "showOutro": true
        }
    """.trimIndent()

    @Test
    fun `v1 JSON sample migrates to v2 timeline with identical scene order, text, backgrounds, voice files, and durations`() {
        assertTrue(ProjectDocumentMigrator.isV1Json(realV1SampleJson))
        assertFalse(ProjectDocumentMigrator.isV2Json(realV1SampleJson))

        val originalV1 = ProjectDocumentMigrator.json.decodeFromString<VideoProjectPayload>(realV1SampleJson)
        val timelineV2 = ProjectDocumentMigrator.migrateV1JsonToV2(
            projectId = "project-v1-draft",
            canvasRatio = "9:16",
            payloadJson = realV1SampleJson
        )

        assertEquals("project-v1-draft", timelineV2.id)
        assertEquals(2, timelineV2.version)
        assertEquals("9:16", timelineV2.canvasRatio)
        assertEquals(30, timelineV2.fps)
        assertEquals(3, timelineV2.sceneBlocks.size)

        // Verify Scene 1 (effective duration = 2800 + 400 = 3200 ms, starts at 0)
        val block1 = timelineV2.sceneBlocks[0]
        assertEquals("scene-intro-01", block1.id)
        val bg1 = timelineV2.findClip(block1.backgroundClipId)!!
        val txt1 = timelineV2.findClip(block1.textClipId)!!
        val logo1 = timelineV2.findClip(block1.logoClipId)!!
        val voice1 = timelineV2.findClip(block1.voiceClipId!!)!!
        assertEquals(0L, bg1.startMs)
        assertEquals(3_200L, bg1.durationMs)
        assertEquals("Your brand story starts here.\nIdeas that move people.", txt1.text)
        assertEquals("scene_statement", bg1.templateId)
        assertEquals(2, logo1.layerOrder)
        assertEquals("/data/user/0/com.sdtech.studio/files/voiceovers/p1/intro.m4a", voice1.sourceUri)
        assertEquals("/data/user/0/com.sdtech.studio/files/voiceovers/p1/intro_raw.m4a", voice1.originalSourceUri)
        assertEquals(-16.1, voice1.normalizedLufs!!, 0.001)
        assertEquals(listOf(0.15f, 0.45f, 0.82f, 0.61f, 0.25f), voice1.waveformLevels)

        // Verify Scene 2 (manual duration = 5000 ms, starts at 3200 ms, video clip trim 1200..6200)
        val block2 = timelineV2.sceneBlocks[1]
        val bg2 = timelineV2.findClip(block2.backgroundClipId)!!
        assertEquals(3_200L, bg2.startMs)
        assertEquals(5_000L, bg2.durationMs)
        assertEquals(1_200L, bg2.sourceInMs)
        assertEquals(6_200L, bg2.sourceOutMs)
        assertEquals(SceneBackgroundType.VIDEO_CLIP, bg2.backgroundType)
        assertEquals("scene_bullet", bg2.templateId)

        // Verify Scene 3 (fallback duration = 3000 ms, starts at 8200 ms)
        val block3 = timelineV2.sceneBlocks[2]
        val bg3 = timelineV2.findClip(block3.backgroundClipId)!!
        assertEquals(8_200L, bg3.startMs)
        assertEquals(3_000L, bg3.durationMs)
        assertEquals("scene_outro", bg3.templateId)

        // Verify total timeline duration and 100% lossless round-trip back to VideoProjectPayload
        assertEquals(VideoDurationCalculator.totalDurationMs(originalV1.scenes), timelineV2.totalDurationMs)
        val roundTrippedV1 = timelineV2.toLegacyPayload()
        assertEquals(originalV1, roundTrippedV1)
    }

    @Test
    fun `project with 200 clips serializes and loads in under 300 ms`() {
        val videoClips = (0 until 50).map { i ->
            Clip(
                id = "vid_$i",
                trackId = "track_video",
                startMs = i * 3_000L,
                durationMs = 3_000L,
                sourceInMs = 0L,
                sourceOutMs = 3_000L,
                layerOrder = 0,
                sourceUri = "content://media/external/video/$i",
                effects = listOf(ClipEffect(type = "COLOR_GRADE", intensity = 0.8f)),
                keyframes = listOf(
                    Keyframe(timeOffsetMs = 0L, property = KeyframeProperty.OPACITY, value = 0f),
                    Keyframe(timeOffsetMs = 300L, property = KeyframeProperty.OPACITY, value = 1f)
                ),
                animations = AnimationPresets(inPreset = AnimationPreset.FADE_IN, outPreset = AnimationPreset.FADE_OUT)
            )
        }
        val textClips = (0 until 50).map { i ->
            Clip(
                id = "txt_$i",
                trackId = "track_text",
                startMs = i * 3_000L,
                durationMs = 3_000L,
                layerOrder = 1,
                text = "Scene headline number $i with descriptive subtitle copy"
            )
        }
        val overlayClips = (0 until 50).map { i ->
            Clip(
                id = "ovl_$i",
                trackId = "track_overlay",
                startMs = i * 3_000L,
                durationMs = 3_000L,
                layerOrder = 2,
                transform = ClipTransform(x = 0.85f, y = 0.08f, scale = 1f, rotation = 0f, opacity = 1f)
            )
        }
        val voiceClips = (0 until 50).map { i ->
            Clip(
                id = "vo_$i",
                trackId = "track_audio_voice",
                startMs = i * 3_000L,
                durationMs = 2_600L,
                sourceUri = "/data/user/0/com.sdtech.studio/files/voiceovers/vo_$i.m4a",
                waveformLevels = List(24) { idx -> (idx % 10) / 10f }
            )
        }

        val project200 = TimelineProject(
            id = "perf_200_clips",
            canvasRatio = "9:16",
            fps = 30,
            tracks = listOf(
                Track(id = "track_video", type = TrackType.VIDEO, clips = videoClips),
                Track(id = "track_overlay", type = TrackType.OVERLAY, clips = overlayClips),
                Track(id = "track_text", type = TrackType.TEXT, clips = textClips),
                Track(id = "track_caption", type = TrackType.CAPTION, clips = emptyList()),
                Track(id = "track_audio_voice", type = TrackType.AUDIO_VOICE, clips = voiceClips),
                Track(id = "track_audio_music", type = TrackType.AUDIO_MUSIC, clips = emptyList()),
                Track(id = "track_audio_sfx", type = TrackType.AUDIO_SFX, clips = emptyList())
            )
        )
        assertEquals(200, project200.clipCount)

        val encodedJson = ProjectDocumentMigrator.encodeProjectDocument(project200)
        // Warm up serializer once
        ProjectDocumentMigrator.decodeProjectDocument("perf_200_clips", "9:16", encodedJson)

        val startNanos = System.nanoTime()
        val loaded = ProjectDocumentMigrator.decodeProjectDocument("perf_200_clips", "9:16", encodedJson)
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0

        assertEquals(200, loaded.clipCount)
        assertTrue("Expected 200-clip project to load in < 300 ms, but took ${elapsedMs}ms", elapsedMs < 300.0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `autosave triggers 1_5s after last edit and coalesces rapid edits`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val savedProjects = mutableListOf<TimelineProject>()
        val controller = TimelineAutosaveController(
            scope = scope,
            debounceMs = 1_500L,
            onSave = { savedProjects.add(it) }
        )

        val p1 = TimelineProject(id = "auto_1", canvasRatio = "9:16")
        val p2 = TimelineProject(id = "auto_1", canvasRatio = "1:1")
        val p3 = TimelineProject(id = "auto_1", canvasRatio = "16:9")

        controller.onEdit(p1)
        advanceTimeBy(600L)
        assertEquals(0, savedProjects.size)

        controller.onEdit(p2)
        advanceTimeBy(900L)
        assertEquals(0, savedProjects.size)

        controller.onEdit(p3)
        advanceTimeBy(1_499L)
        assertEquals(0, savedProjects.size)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(1, savedProjects.size)
        assertEquals("16:9", savedProjects.single().canvasRatio)
        assertFalse(controller.isDirty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `autosave flushes immediately on stop before 1_5s timer expires`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val savedProjects = mutableListOf<TimelineProject>()
        val controller = TimelineAutosaveController(
            scope = scope,
            debounceMs = 1_500L,
            onSave = { savedProjects.add(it) }
        )

        val edited = TimelineProject(id = "auto_stop", canvasRatio = "4:5")
        controller.onEdit(edited)
        advanceTimeBy(200L)
        assertEquals(0, savedProjects.size)

        controller.onStop()
        runCurrent()
        assertEquals(1, savedProjects.size)
        assertEquals("4:5", savedProjects.single().canvasRatio)
        assertFalse(controller.isDirty)
    }

    @Test
    fun `media reference resolver never copies when permission is persistable or file is local, copies only when non-persistable`() {
        var copyCount = 0
        val fakeGateway = object : MediaPersistenceGateway {
            override fun isLocalFileOrAppPath(uriOrPath: String): Boolean =
                uriOrPath.startsWith("/") || uriOrPath.startsWith("file://")

            override fun tryTakePersistableReadPermission(uriString: String): Boolean =
                uriString.contains("persistable=true")

            override fun copyToAppStorage(uriString: String, preferredExtension: String): String {
                copyCount++
                return "file:///data/user/0/com.sdtech.studio/files/project_media/copied_$copyCount.$preferredExtension"
            }
        }
        val resolver = MediaReferenceResolver(fakeGateway)

        // 1. Local file path -> never copied
        val localResult = resolver.resolve("/data/user/0/com.sdtech.studio/files/voiceovers/v1.m4a", "m4a")
        assertFalse(localResult.copiedToAppStorage)
        assertEquals("/data/user/0/com.sdtech.studio/files/voiceovers/v1.m4a", localResult.storedUriOrPath)
        assertEquals(0, copyCount)

        // 2. Persistable content URI -> never copied
        val persistableResult = resolver.resolve("content://media/picker/1?persistable=true", "mp4")
        assertFalse(persistableResult.copiedToAppStorage)
        assertEquals("content://media/picker/1?persistable=true", persistableResult.storedUriOrPath)
        assertEquals(0, copyCount)

        // 3. Non-persistable content URI -> copied to app storage
        val nonPersistableResult = resolver.resolve("content://ephemeral.provider/clip/99?persistable=false", "mp4")
        assertTrue(nonPersistableResult.copiedToAppStorage)
        assertNotNull(nonPersistableResult.storedUriOrPath)
        assertTrue(nonPersistableResult.storedUriOrPath.endsWith(".mp4"))
        assertEquals(1, copyCount)
    }
}
