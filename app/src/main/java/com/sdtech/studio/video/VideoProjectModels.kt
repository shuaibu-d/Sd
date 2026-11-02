package com.sdtech.studio.video

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SceneType { INTRO, CONTENT, OUTRO }

@Serializable
enum class SceneBackgroundType { BRAND_COLOR, GRADIENT, IMAGE, VIDEO_CLIP }

@Serializable
enum class SceneTransition { NONE, FADE }

@Serializable
enum class SceneTextStyle { BIG_STATEMENT, BULLET_POINT, HIGHLIGHTED_KEYWORD }

@Serializable
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val type: SceneType = SceneType.CONTENT,
    val text: String = "",
    val textStyle: SceneTextStyle = SceneTextStyle.BIG_STATEMENT,
    val backgroundType: SceneBackgroundType = SceneBackgroundType.BRAND_COLOR,
    val backgroundUri: String? = null,
    /** Optional source clip trim range in milliseconds. */
    val clipTrimStartMs: Long = 0L,
    val clipTrimEndMs: Long? = null,
    val voiceFilePath: String? = null,
    /** Original unprocessed take retained when a normalized derivative is active. */
    val voiceOriginalFilePath: String? = null,
    val normalizedLufs: Double? = null,
    val normalizedTruePeakDbtp: Double? = null,
    val normalizationGainDb: Double? = null,
    val voiceDurationMs: Long? = null,
    val waveformLevels: List<Float> = emptyList(),
    val manualDurationMs: Long? = null,
    val transition: SceneTransition = SceneTransition.FADE
)

@Serializable
data class VideoProjectPayload(
    val version: Int = 1,
    val scenes: List<Scene>,
    val showIntro: Boolean = true,
    val showOutro: Boolean = true
)

/** Manual timing overrides the voice take; otherwise leave a short 400 ms visual tail. */
val Scene.effectiveDurationMs: Long?
    get() = manualDurationMs ?: voiceDurationMs?.plus(400L)

object ScriptSceneSplitter {
    const val MAX_WORDS_PER_SCENE = 18
    const val MAX_SCENES = 40

    fun split(script: String): List<String> {
        val paragraphs = script.trim().split(Regex("\\n\\s*\\n"))
            .map(String::trim).filter(String::isNotBlank)
        val source = if (paragraphs.isNotEmpty()) paragraphs else listOf(script.trim())
        return source.flatMap { paragraph ->
            val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
                .map(String::trim).filter(String::isNotBlank)
            sentences.flatMap { sentence ->
                val words = sentence.split(Regex("\\s+")).filter(String::isNotBlank)
                words.chunked(MAX_WORDS_PER_SCENE).map { it.joinToString(" ") }
            }
        }.filter(String::isNotBlank).take(MAX_SCENES)
    }

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
}

/** Predictable total timeline length when scenes do not yet have a voice recording. */
object VideoDurationCalculator {
    const val EMPTY_SCENE_FALLBACK_MS = 3_000L
    fun sceneDurationMs(scene: Scene, fallbackMs: Long = EMPTY_SCENE_FALLBACK_MS, minimumMs: Long = 1L): Long =
        (scene.effectiveDurationMs ?: fallbackMs).coerceAtLeast(minimumMs)
    fun totalDurationMs(scenes: List<Scene>): Long = scenes.sumOf { sceneDurationMs(it) }
}
