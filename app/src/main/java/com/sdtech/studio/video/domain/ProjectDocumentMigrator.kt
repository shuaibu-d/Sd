package com.sdtech.studio.video.domain

import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.VideoDurationCalculator
import com.sdtech.studio.video.VideoProjectPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object SceneBlockFactory {
    fun templateIdFor(sceneType: SceneType, textStyle: SceneTextStyle): String = when {
        sceneType == SceneType.OUTRO -> "scene_outro"
        textStyle == SceneTextStyle.BULLET_POINT -> "scene_bullet"
        else -> "scene_statement"
    }

    data class BuiltSceneBlock(
        val sceneBlock: SceneBlock,
        val backgroundClip: Clip,
        val textClip: Clip,
        val logoClip: Clip,
        val voiceClip: Clip?
    )

    fun fromScene(
        scene: Scene,
        startMs: Long,
        videoTrackId: String = "track_video",
        textTrackId: String = "track_text",
        overlayTrackId: String = "track_overlay",
        voiceTrackId: String = "track_audio_voice"
    ): BuiltSceneBlock {
        val durationMs = VideoDurationCalculator.sceneDurationMs(scene)
        val templateId = templateIdFor(scene.type, scene.textStyle)
        val groupId = scene.id
        val bgClipId = "${scene.id}_bg"
        val textClipId = "${scene.id}_text"
        val logoClipId = "${scene.id}_logo"
        val hasVoiceData = !scene.voiceFilePath.isNullOrBlank() ||
            !scene.voiceOriginalFilePath.isNullOrBlank() ||
            scene.voiceDurationMs != null ||
            scene.waveformLevels.isNotEmpty() ||
            scene.normalizedLufs != null ||
            scene.normalizedTruePeakDbtp != null ||
            scene.normalizationGainDb != null
        val voiceClipId = if (hasVoiceData) "${scene.id}_voice" else null

        val inAnim = if (scene.transition == SceneTransition.FADE) {
            AnimationPreset.FADE_IN
        } else {
            AnimationPreset.NONE
        }

        val trimStart = scene.clipTrimStartMs.coerceAtLeast(0L)
        val trimEnd = scene.clipTrimEndMs ?: (trimStart + durationMs)

        val backgroundClip = Clip(
            id = bgClipId,
            trackId = videoTrackId,
            startMs = startMs,
            durationMs = durationMs,
            sourceInMs = trimStart,
            sourceOutMs = trimEnd,
            layerOrder = 0,
            animations = AnimationPresets(inPreset = inAnim),
            groupId = groupId,
            sourceUri = scene.backgroundUri,
            backgroundType = scene.backgroundType,
            templateId = templateId,
            sceneType = scene.type,
            transition = scene.transition,
            voiceDurationMs = scene.voiceDurationMs,
            manualDurationMs = scene.manualDurationMs,
            hasExplicitTrimEnd = scene.clipTrimEndMs != null,
            metadata = mapOf("role" to "background", "sceneId" to scene.id)
        )

        val textClip = Clip(
            id = textClipId,
            trackId = textTrackId,
            startMs = startMs,
            durationMs = durationMs,
            sourceInMs = 0L,
            sourceOutMs = durationMs,
            layerOrder = 1,
            animations = AnimationPresets(inPreset = inAnim),
            groupId = groupId,
            text = scene.text,
            textStyle = scene.textStyle,
            templateId = templateId,
            sceneType = scene.type,
            transition = scene.transition,
            metadata = mapOf("role" to "text", "sceneId" to scene.id)
        )

        val logoClip = Clip(
            id = logoClipId,
            trackId = overlayTrackId,
            startMs = startMs,
            durationMs = durationMs,
            sourceInMs = 0L,
            sourceOutMs = durationMs,
            layerOrder = 2,
            transform = ClipTransform(x = 0.85f, y = 0.08f, scale = 1f, rotation = 0f, opacity = 1f),
            groupId = groupId,
            templateId = templateId,
            sceneType = scene.type,
            metadata = mapOf("role" to "logo", "placementMode" to "SAFE", "sceneId" to scene.id)
        )

        val voiceClip = if (hasVoiceData) {
            val vDur = scene.voiceDurationMs ?: durationMs
            Clip(
                id = voiceClipId!!,
                trackId = voiceTrackId,
                startMs = startMs,
                durationMs = vDur,
                sourceInMs = 0L,
                sourceOutMs = vDur,
                layerOrder = 0,
                groupId = groupId,
                sourceUri = scene.voiceFilePath,
                originalSourceUri = scene.voiceOriginalFilePath,
                normalizedLufs = scene.normalizedLufs,
                normalizedTruePeakDbtp = scene.normalizedTruePeakDbtp,
                normalizationGainDb = scene.normalizationGainDb,
                voiceDurationMs = scene.voiceDurationMs,
                waveformLevels = scene.waveformLevels,
                metadata = mapOf("role" to "voice", "sceneId" to scene.id)
            )
        } else {
            null
        }

        val block = SceneBlock(
            id = scene.id,
            sceneType = scene.type,
            templateId = templateId,
            backgroundClipId = bgClipId,
            textClipId = textClipId,
            logoClipId = logoClipId,
            voiceClipId = voiceClipId,
            clipIds = listOfNotNull(bgClipId, textClipId, logoClipId, voiceClipId),
            isGrouped = true,
            transition = scene.transition,
            manualDurationMs = scene.manualDurationMs
        )

        return BuiltSceneBlock(
            sceneBlock = block,
            backgroundClip = backgroundClip,
            textClip = textClip,
            logoClip = logoClip,
            voiceClip = voiceClip
        )
    }
}

fun TimelineProject.toScenes(): List<Scene> {
    val clipsById = HashMap<String, Clip>(clipCount * 2)
    for (track in tracks) {
        for (clip in track.clips) {
            clipsById[clip.id] = clip
        }
    }

    if (sceneBlocks.isNotEmpty()) {
        return sceneBlocks.mapNotNull { block ->
            val bgClip = clipsById[block.backgroundClipId]
            val textClip = clipsById[block.textClipId]
            val voiceClip = block.voiceClipId?.let { clipsById[it] }
                ?: tracks.firstOrNull { it.type == TrackType.AUDIO_VOICE }
                    ?.clips
                    ?.firstOrNull { it.groupId == block.id || it.metadata["sceneId"] == block.id }

            if (bgClip == null && textClip == null) return@mapNotNull null

            val resolvedManualDuration = bgClip?.manualDurationMs ?: block.manualDurationMs
            val resolvedVoiceDuration = voiceClip?.voiceDurationMs ?: bgClip?.voiceDurationMs
            val clipTrimStart = bgClip?.sourceInMs ?: 0L
            val clipTrimEnd = if (bgClip?.hasExplicitTrimEnd == true) bgClip.sourceOutMs else null

            Scene(
                id = block.id,
                type = bgClip?.sceneType ?: textClip?.sceneType ?: block.sceneType,
                text = textClip?.text ?: bgClip?.text.orEmpty(),
                textStyle = textClip?.textStyle ?: SceneTextStyle.BIG_STATEMENT,
                backgroundType = bgClip?.backgroundType ?: SceneBackgroundType.BRAND_COLOR,
                backgroundUri = bgClip?.sourceUri,
                clipTrimStartMs = clipTrimStart,
                clipTrimEndMs = clipTrimEnd,
                voiceFilePath = voiceClip?.sourceUri,
                voiceOriginalFilePath = voiceClip?.originalSourceUri,
                normalizedLufs = voiceClip?.normalizedLufs,
                normalizedTruePeakDbtp = voiceClip?.normalizedTruePeakDbtp,
                normalizationGainDb = voiceClip?.normalizationGainDb,
                voiceDurationMs = resolvedVoiceDuration,
                waveformLevels = voiceClip?.waveformLevels.orEmpty(),
                manualDurationMs = resolvedManualDuration,
                transition = bgClip?.transition ?: block.transition
            )
        }
    }

    // Fallback when a TimelineProject has raw clips on VIDEO / TEXT tracks without sceneBlocks
    val videoClips = findTrackByType(TrackType.VIDEO)?.clips?.sortedBy { it.startMs }.orEmpty()
    val textClips = findTrackByType(TrackType.TEXT)?.clips?.sortedBy { it.startMs }.orEmpty()
    val voiceClips = findTrackByType(TrackType.AUDIO_VOICE)?.clips?.sortedBy { it.startMs }.orEmpty()
    if (videoClips.isEmpty() && textClips.isEmpty()) return emptyList()

    val count = maxOf(videoClips.size, textClips.size)
    return (0 until count).map { idx ->
        val bg = videoClips.getOrNull(idx)
        val txt = textClips.getOrNull(idx)
        val voice = voiceClips.getOrNull(idx)
        Scene(
            id = bg?.groupId ?: txt?.groupId ?: bg?.id ?: txt?.id ?: "scene_$idx",
            type = bg?.sceneType ?: txt?.sceneType ?: SceneType.CONTENT,
            text = txt?.text ?: bg?.text.orEmpty(),
            textStyle = txt?.textStyle ?: SceneTextStyle.BIG_STATEMENT,
            backgroundType = bg?.backgroundType ?: SceneBackgroundType.BRAND_COLOR,
            backgroundUri = bg?.sourceUri,
            clipTrimStartMs = bg?.sourceInMs ?: 0L,
            clipTrimEndMs = if (bg?.hasExplicitTrimEnd == true) bg.sourceOutMs else null,
            voiceFilePath = voice?.sourceUri,
            voiceOriginalFilePath = voice?.originalSourceUri,
            normalizedLufs = voice?.normalizedLufs,
            normalizedTruePeakDbtp = voice?.normalizedTruePeakDbtp,
            normalizationGainDb = voice?.normalizationGainDb,
            voiceDurationMs = voice?.voiceDurationMs ?: bg?.voiceDurationMs,
            waveformLevels = voice?.waveformLevels.orEmpty(),
            manualDurationMs = bg?.manualDurationMs ?: bg?.durationMs,
            transition = bg?.transition ?: SceneTransition.FADE
        )
    }
}

fun TimelineProject.toLegacyPayload(): VideoProjectPayload {
    val scenes = toScenes()
    return VideoProjectPayload(
        version = 1,
        scenes = scenes,
        showIntro = showIntro && scenes.any { it.type == SceneType.INTRO },
        showOutro = showOutro && scenes.any { it.type == SceneType.OUTRO }
    )
}

object ProjectDocumentMigrator {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Returns true if the JSON string represents a v1 VideoProjectPayload document.
     */
    fun isV1Json(payloadJson: String): Boolean {
        val trimmed = payloadJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return false
        return runCatching {
            val obj = json.decodeFromString<JsonObject>(trimmed)
            val version = obj["version"]?.jsonPrimitive?.intOrNull ?: 1
            version == 1 && obj.containsKey("scenes") && !obj.containsKey("tracks")
        }.getOrDefault(false)
    }

    /**
     * Returns true if the JSON string represents a v2 TimelineProject document.
     */
    fun isV2Json(payloadJson: String): Boolean {
        val trimmed = payloadJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return false
        return runCatching {
            val obj = json.decodeFromString<JsonObject>(trimmed)
            val version = obj["version"]?.jsonPrimitive?.intOrNull
            version == TimelineProject.CURRENT_VERSION && obj.containsKey("tracks")
        }.getOrDefault(false)
    }

    /**
     * Converts a v1 [VideoProjectPayload] into a v2 [TimelineProject] with identical scene order,
     * text, backgrounds, voice files, normalization parameters, waveforms, and durations.
     */
    fun migrateV1ToV2(
        projectId: String,
        canvasRatio: String = "9:16",
        payload: VideoProjectPayload,
        fps: Int = TimelineProject.DEFAULT_FPS,
        createdAtMs: Long = 0L,
        updatedAtMs: Long = 0L
    ): TimelineProject {
        val videoClips = ArrayList<Clip>(payload.scenes.size)
        val textClips = ArrayList<Clip>(payload.scenes.size)
        val logoClips = ArrayList<Clip>(payload.scenes.size)
        val voiceClips = ArrayList<Clip>(payload.scenes.size)
        val blocks = ArrayList<SceneBlock>(payload.scenes.size)

        var cursorMs = 0L
        for (scene in payload.scenes) {
            val built = SceneBlockFactory.fromScene(
                scene = scene,
                startMs = cursorMs
            )
            videoClips.add(built.backgroundClip)
            textClips.add(built.textClip)
            logoClips.add(built.logoClip)
            if (built.voiceClip != null) {
                voiceClips.add(built.voiceClip)
            }
            blocks.add(built.sceneBlock)
            cursorMs += built.backgroundClip.durationMs
        }

        val tracks = listOf(
            Track(
                id = "track_video",
                type = TrackType.VIDEO,
                name = "Video / Background",
                layerOrder = 0,
                clips = videoClips
            ),
            Track(
                id = "track_overlay",
                type = TrackType.OVERLAY,
                name = "Overlay / Logo",
                layerOrder = 1,
                clips = logoClips
            ),
            Track(
                id = "track_text",
                type = TrackType.TEXT,
                name = "Text",
                layerOrder = 2,
                clips = textClips
            ),
            Track(
                id = "track_caption",
                type = TrackType.CAPTION,
                name = "Captions",
                layerOrder = 3,
                clips = emptyList()
            ),
            Track(
                id = "track_audio_voice",
                type = TrackType.AUDIO_VOICE,
                name = "Voice-Over",
                layerOrder = 4,
                clips = voiceClips
            ),
            Track(
                id = "track_audio_music",
                type = TrackType.AUDIO_MUSIC,
                name = "Background Music",
                layerOrder = 5,
                clips = emptyList()
            ),
            Track(
                id = "track_audio_sfx",
                type = TrackType.AUDIO_SFX,
                name = "Sound Effects",
                layerOrder = 6,
                clips = emptyList()
            )
        )

        return TimelineProject(
            id = projectId,
            version = TimelineProject.CURRENT_VERSION,
            canvasRatio = canvasRatio,
            fps = fps,
            tracks = tracks,
            sceneBlocks = blocks,
            showIntro = payload.showIntro,
            showOutro = payload.showOutro,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }

    /**
     * Parses a v1 JSON payload and converts it into a v2 [TimelineProject].
     */
    fun migrateV1JsonToV2(
        projectId: String,
        canvasRatio: String = "9:16",
        payloadJson: String,
        fps: Int = TimelineProject.DEFAULT_FPS,
        createdAtMs: Long = 0L,
        updatedAtMs: Long = 0L
    ): TimelineProject {
        val v1 = json.decodeFromString<VideoProjectPayload>(payloadJson)
        return migrateV1ToV2(
            projectId = projectId,
            canvasRatio = canvasRatio,
            payload = v1,
            fps = fps,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }

    /**
     * Converts a v1 JSON payload string directly to a v2 JSON payload string.
     * If the input is already v2 JSON, it returns it unchanged.
     */
    fun migrateV1JsonToV2Json(
        projectId: String,
        canvasRatio: String = "9:16",
        payloadJson: String,
        fps: Int = TimelineProject.DEFAULT_FPS,
        createdAtMs: Long = 0L,
        updatedAtMs: Long = 0L
    ): String {
        val trimmed = payloadJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return payloadJson
        if (isV2Json(trimmed)) return payloadJson
        return runCatching {
            val migrated = migrateV1JsonToV2(
                projectId = projectId,
                canvasRatio = canvasRatio,
                payloadJson = trimmed,
                fps = fps,
                createdAtMs = createdAtMs,
                updatedAtMs = updatedAtMs
            )
            encodeProjectDocument(migrated)
        }.getOrDefault(payloadJson)
    }

    /**
     * Decodes either a v2 [TimelineProject] JSON or a v1 [VideoProjectPayload] JSON
     * seamlessly into a v2 [TimelineProject].
     */
    fun decodeProjectDocument(
        projectId: String,
        canvasRatio: String = "9:16",
        payloadJson: String
    ): TimelineProject {
        val trimmed = payloadJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") {
            return TimelineProject(id = projectId, canvasRatio = canvasRatio)
        }
        // Fast path for v2 documents: check if it contains "tracks"
        if (trimmed.contains("\"tracks\"")) {
            runCatching {
                val decoded = json.decodeFromString<TimelineProject>(trimmed)
                return if (decoded.id.isBlank()) decoded.copy(id = projectId) else decoded
            }
        }
        // Fallback migration path for v1 documents
        return migrateV1JsonToV2(
            projectId = projectId,
            canvasRatio = canvasRatio,
            payloadJson = trimmed
        )
    }

    /**
     * Decodes either a v1 or v2 JSON document into a [VideoProjectPayload] for legacy/scene consumers.
     */
    fun decodeAsLegacyPayload(
        projectId: String,
        canvasRatio: String = "9:16",
        payloadJson: String
    ): VideoProjectPayload {
        val trimmed = payloadJson.trim()
        if (trimmed.isEmpty() || trimmed == "{}") {
            return VideoProjectPayload(scenes = emptyList())
        }
        if (!trimmed.contains("\"tracks\"")) {
            runCatching {
                return json.decodeFromString<VideoProjectPayload>(trimmed)
            }
        }
        return decodeProjectDocument(projectId, canvasRatio, trimmed).toLegacyPayload()
    }

    fun encodeProjectDocument(project: TimelineProject): String =
        json.encodeToString(TimelineProject.serializer(), project)

    fun fromScenes(
        projectId: String,
        canvasRatio: String,
        scenes: List<Scene>,
        fps: Int = TimelineProject.DEFAULT_FPS,
        createdAtMs: Long = 0L,
        updatedAtMs: Long = 0L
    ): TimelineProject {
        val payload = VideoProjectPayload(
            version = 1,
            scenes = scenes,
            showIntro = scenes.any { it.type == SceneType.INTRO },
            showOutro = scenes.any { it.type == SceneType.OUTRO }
        )
        return migrateV1ToV2(
            projectId = projectId,
            canvasRatio = canvasRatio,
            payload = payload,
            fps = fps,
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }
}
