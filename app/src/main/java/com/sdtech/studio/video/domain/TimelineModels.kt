package com.sdtech.studio.video.domain

import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneTransition
import com.sdtech.studio.video.SceneType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TrackType {
    VIDEO,
    OVERLAY,
    TEXT,
    AUDIO_MUSIC,
    AUDIO_VOICE,
    AUDIO_SFX,
    CAPTION
}

@Serializable
data class ClipTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f
)

@Serializable
data class ClipEffect(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val enabled: Boolean = true,
    val intensity: Float = 1f,
    val params: Map<String, String> = emptyMap()
)

@Serializable
enum class KeyframeProperty {
    X,
    Y,
    SCALE,
    ROTATION,
    OPACITY,
    VOLUME
}

@Serializable
enum class EasingCurve {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    CUBIC_BEZIER
}

@Serializable
data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val property: KeyframeProperty,
    val value: Float,
    val easing: EasingCurve = EasingCurve.LINEAR
)

@Serializable
enum class AnimationType {
    NONE,
    FADE,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    POP,
    ZOOM_IN,
    ZOOM_OUT,
    PULSE,
    FLOAT,
    SPIN
}

@Serializable
data class AnimationPreset(
    val type: AnimationType = AnimationType.NONE,
    val durationMs: Long = 0L
) {
    companion object {
        val NONE = AnimationPreset(AnimationType.NONE, 0L)
        val FADE_IN = AnimationPreset(AnimationType.FADE, 300L)
        val FADE_OUT = AnimationPreset(AnimationType.FADE, 300L)
        val SLIDE_UP = AnimationPreset(AnimationType.SLIDE_UP, 350L)
        val PULSE_LOOP = AnimationPreset(AnimationType.PULSE, 1_000L)
    }
}

@Serializable
data class AnimationPresets(
    @SerialName("in")
    val inPreset: AnimationPreset = AnimationPreset.NONE,
    @SerialName("out")
    val outPreset: AnimationPreset = AnimationPreset.NONE,
    @SerialName("loop")
    val loopPreset: AnimationPreset = AnimationPreset.NONE
)

@Serializable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val trackId: String = "",
    val startMs: Long = 0L,
    val durationMs: Long = 3_000L,
    val sourceInMs: Long = 0L,
    val sourceOutMs: Long = 3_000L,
    val layerOrder: Int = 0,
    val transform: ClipTransform = ClipTransform(),
    val effects: List<ClipEffect> = emptyList(),
    val keyframes: List<Keyframe> = emptyList(),
    val animations: AnimationPresets = AnimationPresets(),
    val groupId: String? = null,
    val sourceUri: String? = null,
    val originalSourceUri: String? = null,
    val text: String = "",
    val textStyle: SceneTextStyle = SceneTextStyle.BIG_STATEMENT,
    val backgroundType: SceneBackgroundType = SceneBackgroundType.BRAND_COLOR,
    val colorHex: String? = null,
    val secondaryColorHex: String? = null,
    val templateId: String? = null,
    val sceneType: SceneType = SceneType.CONTENT,
    val transition: SceneTransition = SceneTransition.FADE,
    val volume: Float = 1f,
    val normalizedLufs: Double? = null,
    val normalizedTruePeakDbtp: Double? = null,
    val normalizationGainDb: Double? = null,
    val voiceDurationMs: Long? = null,
    val manualDurationMs: Long? = null,
    val waveformLevels: List<Float> = emptyList(),
    val hasExplicitTrimEnd: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) {
    val endMs: Long
        get() = startMs + durationMs
}

@Serializable
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val name: String = type.name,
    val layerOrder: Int = 0,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val clips: List<Clip> = emptyList()
)

/**
 * Represents a Scene template unit in v2: a grouped set of clips
 * (background on VIDEO track + text on TEXT track + logo on OVERLAY track, plus optional voice clip)
 * that preserves today's scene template rendering and can be edited as one unit or ungrouped.
 */
@Serializable
data class SceneBlock(
    val id: String = UUID.randomUUID().toString(),
    val sceneType: SceneType = SceneType.CONTENT,
    val templateId: String = "scene_statement",
    val backgroundClipId: String,
    val textClipId: String,
    val logoClipId: String,
    val voiceClipId: String? = null,
    val clipIds: List<String> = listOfNotNull(backgroundClipId, textClipId, logoClipId, voiceClipId),
    val isGrouped: Boolean = true,
    val transition: SceneTransition = SceneTransition.FADE,
    val manualDurationMs: Long? = null
)

@Serializable
data class TimelineProject(
    val id: String = UUID.randomUUID().toString(),
    val version: Int = CURRENT_VERSION,
    val canvasRatio: String = "9:16",
    val fps: Int = DEFAULT_FPS,
    val tracks: List<Track> = defaultTracks(),
    val sceneBlocks: List<SceneBlock> = emptyList(),
    val showIntro: Boolean = true,
    val showOutro: Boolean = true,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    val totalDurationMs: Long
        get() = tracks.maxOfOrNull { track ->
            track.clips.maxOfOrNull { it.endMs } ?: 0L
        } ?: 0L

    val clipCount: Int
        get() = tracks.sumOf { it.clips.size }

    val allClips: List<Clip>
        get() = tracks.flatMap { it.clips }

    fun findTrack(trackId: String): Track? =
        tracks.firstOrNull { it.id == trackId }

    fun findTrackByType(type: TrackType): Track? =
        tracks.firstOrNull { it.type == type }

    fun findClip(clipId: String): Clip? {
        for (track in tracks) {
            val found = track.clips.firstOrNull { it.id == clipId }
            if (found != null) return found
        }
        return null
    }

    fun findTrackForClip(clipId: String): Track? =
        tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }

    fun findSceneBlock(blockId: String): SceneBlock? =
        sceneBlocks.firstOrNull { it.id == blockId }

    fun findSceneBlockForClip(clipId: String): SceneBlock? =
        sceneBlocks.firstOrNull { block -> block.clipIds.contains(clipId) }

    fun getGroupedClips(groupId: String): List<Clip> =
        tracks.flatMap { track -> track.clips.filter { it.groupId == groupId } }

    companion object {
        const val CURRENT_VERSION = 2
        const val DEFAULT_FPS = 30

        fun defaultTracks(): List<Track> = listOf(
            Track(id = "track_video", type = TrackType.VIDEO, name = "Video / Background", layerOrder = 0),
            Track(id = "track_overlay", type = TrackType.OVERLAY, name = "Overlay / Logo", layerOrder = 1),
            Track(id = "track_text", type = TrackType.TEXT, name = "Text", layerOrder = 2),
            Track(id = "track_caption", type = TrackType.CAPTION, name = "Captions", layerOrder = 3),
            Track(id = "track_audio_voice", type = TrackType.AUDIO_VOICE, name = "Voice-Over", layerOrder = 4),
            Track(id = "track_audio_music", type = TrackType.AUDIO_MUSIC, name = "Background Music", layerOrder = 5),
            Track(id = "track_audio_sfx", type = TrackType.AUDIO_SFX, name = "Sound Effects", layerOrder = 6)
        )
    }
}
