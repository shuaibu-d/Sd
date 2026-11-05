package com.sdtech.studio.ui.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.LogoAsset
import com.sdtech.studio.media.Media3FrameExtractor
import com.sdtech.studio.performance.CentralBitmapPool
import com.sdtech.studio.render.BackgroundKind
import com.sdtech.studio.render.LogoPlacementMode
import com.sdtech.studio.render.RenderOptions
import com.sdtech.studio.render.ShapeKind
import com.sdtech.studio.render.Template
import com.sdtech.studio.render.TemplateContent
import com.sdtech.studio.render.TemplateLayer
import com.sdtech.studio.render.TemplateRenderer
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.video.Scene
import com.sdtech.studio.video.SceneBackgroundType
import com.sdtech.studio.video.SceneTextStyle
import com.sdtech.studio.video.SceneType
import com.sdtech.studio.video.domain.Clip
import com.sdtech.studio.video.domain.TimelineProject
import com.sdtech.studio.video.domain.TrackType
import java.util.Locale
import kotlin.math.abs

enum class StudioToolTab(
    val label: String,
    val icon: SDIconKind,
    val contentDesc: String
) {
    TEXT("Text", SDIconKind.Text, "Open Text tool panel"),
    MEDIA("Media", SDIconKind.Video, "Open Media and background tool panel"),
    AUDIO("Audio", SDIconKind.Mic, "Open Audio and voice-over tool panel"),
    CAPTIONS("Captions", SDIconKind.Edit, "Open Captions tool panel"),
    BRAND("Brand", SDIconKind.Brand, "Open Brand logo and canvas ratio tool panel"),
    EFFECTS("Effects", SDIconKind.Sparkle, "Open Effects tool panel"),
    TEMPLATES("Templates", SDIconKind.Layers, "Open Scene Templates tool panel");

    companion object {
        fun forTrackType(type: TrackType): StudioToolTab = when (type) {
            TrackType.VIDEO -> MEDIA
            TrackType.TEXT -> TEXT
            TrackType.OVERLAY -> BRAND
            TrackType.CAPTION -> CAPTIONS
            TrackType.AUDIO_VOICE, TrackType.AUDIO_MUSIC, TrackType.AUDIO_SFX -> AUDIO
        }
    }
}

data class SnapResult(
    val snappedMs: Long,
    val didSnap: Boolean,
    val snapGuideMs: Long?
)

object TimelineSnapEngine {
    const val DEFAULT_SNAP_THRESHOLD_MS = 140L
    const val MIN_CLIP_DURATION_MS = 200L

    fun snapTime(
        candidateMs: Long,
        project: TimelineProject,
        playheadMs: Long,
        excludeClipIds: Set<String> = emptySet(),
        thresholdMs: Long = DEFAULT_SNAP_THRESHOLD_MS
    ): SnapResult {
        val clamped = candidateMs.coerceAtLeast(0L)
        val targets = LinkedHashSet<Long>()
        targets.add(0L)
        targets.add(playheadMs.coerceAtLeast(0L))
        val total = project.totalDurationMs
        if (total > 0L) targets.add(total)

        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.id in excludeClipIds) continue
                targets.add(clip.startMs)
                targets.add(clip.endMs)
            }
        }

        var bestTarget: Long? = null
        var bestDist = Long.MAX_VALUE
        for (t in targets) {
            val dist = abs(clamped - t)
            if (dist <= thresholdMs && dist < bestDist) {
                bestDist = dist
                bestTarget = t
            }
        }

        return if (bestTarget != null) {
            SnapResult(snappedMs = bestTarget, didSnap = true, snapGuideMs = bestTarget)
        } else {
            SnapResult(snappedMs = clamped, didSnap = false, snapGuideMs = null)
        }
    }
}

fun formatStudioTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val tenths = (safe % 1_000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}

fun findActiveClipsAtPlayhead(project: TimelineProject, playheadMs: Long): List<Clip> {
    val safePlayhead = playheadMs.coerceAtLeast(0L)
    val active = project.allClips.filter { clip ->
        safePlayhead >= clip.startMs && safePlayhead < clip.endMs
    }
    if (active.isNotEmpty()) return active
    return project.allClips.filter { clip ->
        safePlayhead == clip.endMs
    }
}

suspend fun renderScenePreviewBitmap(
    context: Context,
    projectId: String,
    scene: Scene,
    ratio: String,
    templates: Map<String, Template>,
    brandKit: BrandKit?,
    logos: List<LogoAsset>,
    maxEdgePx: Int
): Bitmap? {
    val cacheKey = "video_scene_thumb:$projectId:${scene.id}:$ratio:${scene.hashCode()}:$maxEdgePx"
    val cached = CentralBitmapPool.get(cacheKey)
    if (cached != null && !cached.isRecycled) return cached

    val templateId = when {
        scene.type == SceneType.OUTRO -> "scene_outro"
        scene.textStyle == SceneTextStyle.BULLET_POINT -> "scene_bullet"
        else -> "scene_statement"
    }
    val original = templates[templateId] ?: return null
    val image = scene.backgroundUri?.let { uriString ->
        runCatching {
            val uri = Uri.parse(uriString)
            if (scene.backgroundType == SceneBackgroundType.VIDEO_CLIP) {
                Media3FrameExtractor.extractFrameCached(
                    context = context,
                    uri = uri,
                    positionMs = scene.clipTrimStartMs.coerceAtLeast(0L),
                    maxEdgePx = maxEdgePx
                )
            } else {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                }
            }
        }.getOrNull()
    }

    val bg = original.layers.filterIsInstance<TemplateLayer.Background>().firstOrNull()
    val adjustedBackground = if (bg != null) {
        val next = when (scene.backgroundType) {
            SceneBackgroundType.BRAND_COLOR -> bg.copy(
                kind = BackgroundKind.SOLID,
                color = brandKit?.palette?.firstOrNull()?.hex ?: "#101C2C"
            )
            SceneBackgroundType.GRADIENT -> bg.copy(
                kind = BackgroundKind.GRADIENT,
                color = "#101C2C",
                endColor = "#16A6A0"
            )
            SceneBackgroundType.IMAGE, SceneBackgroundType.VIDEO_CLIP -> bg.copy(
                kind = BackgroundKind.IMAGE,
                imageAsset = "scene_bg"
            )
        }
        original.copy(layers = original.layers.map { if (it.id == bg.id) next else it })
    } else original

    val withImageOverlay = if (
        scene.backgroundType == SceneBackgroundType.IMAGE ||
        scene.backgroundType == SceneBackgroundType.VIDEO_CLIP
    ) {
        val overlay = TemplateLayer.Shape(
            id = "scene_image_overlay",
            x = 0f,
            y = 0f,
            width = 100f,
            height = 100f,
            shape = ShapeKind.RECTANGLE,
            color = "#66101C2C"
        )
        val firstText = adjustedBackground.layers.indexOfFirst { it is TemplateLayer.Text }
            .let { if (it < 0) adjustedBackground.layers.size else it }
        adjustedBackground.copy(
            layers = adjustedBackground.layers.toMutableList().also { it.add(firstText, overlay) }
        )
    } else adjustedBackground

    val values = when (scene.type) {
        SceneType.OUTRO -> mapOf(
            "headline" to scene.text.lineSequence().firstOrNull().orEmpty(),
            "cta" to scene.text.lineSequence().drop(1).joinToString(" ").ifBlank {
                brandKit?.defaultCta?.takeIf { it.isNotBlank() } ?: "FOLLOW FOR MORE"
            },
            "monogram" to (brandKit?.name?.take(2)?.uppercase() ?: "SD")
        )
        SceneType.INTRO -> mapOf(
            "label" to (brandKit?.name ?: "INTRODUCTION").uppercase(),
            "keyword" to "",
            "headline" to scene.text.lineSequence().firstOrNull().orEmpty(),
            "footer" to scene.text.lineSequence().drop(1).joinToString(" ").ifBlank {
                brandKit?.slogan?.takeIf { it.isNotBlank() } ?: "Create Without Limits."
            }
        )
        SceneType.CONTENT -> {
            val words = scene.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val highlighted = scene.textStyle == SceneTextStyle.HIGHLIGHTED_KEYWORD && words.isNotEmpty()
            mapOf(
                "label" to "THE KEY IDEA",
                "keyword" to if (highlighted) words.first() else "",
                "headline" to when {
                    scene.textStyle == SceneTextStyle.BULLET_POINT -> "• ${scene.text}"
                    highlighted -> words.drop(1).joinToString(" ")
                    else -> scene.text
                },
                "footer" to ""
            )
        }
    }

    return runCatching {
        CentralBitmapPool.getOrPut(cacheKey) {
            TemplateRenderer(context).render(
                withImageOverlay,
                TemplateContent(
                    values = values,
                    images = if (image != null) mapOf("scene_bg" to image) else emptyMap(),
                    brandLock = false
                ),
                ratio,
                theme = withImageOverlay.themes.firstOrNull(),
                options = RenderOptions(
                    logoPlacementMode = LogoPlacementMode.SAFE,
                    brandColors = brandKit?.palette.orEmpty().associate { it.name.lowercase() to it.hex },
                    maxDimension = maxEdgePx
                ),
                brandKit = brandKit,
                logos = logos
            )
        }
    }.getOrNull()
}
