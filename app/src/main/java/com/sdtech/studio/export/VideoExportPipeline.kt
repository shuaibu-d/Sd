@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sdtech.studio.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import com.sdtech.studio.data.local.entity.ExportRecord
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.data.repository.ExportRepository
import com.sdtech.studio.data.repository.ProjectRepository
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.LogoAsset
import com.sdtech.studio.render.*
import com.sdtech.studio.video.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

enum class VideoExportPreset(val label: String, val ratio: String, val width: Int, val height: Int,
                             val fallbackWidth: Int, val fallbackHeight: Int) {
    REELS("Reels / TikTok / Shorts", "9:16", 1080, 1920, 720, 1280),
    FEED("Feed", "4:5", 1080, 1350, 720, 900),
    SQUARE("Square", "1:1", 1080, 1080, 720, 720),
    YOUTUBE("YouTube", "16:9", 1920, 1080, 1280, 720)
}

enum class ExportLogoVariant { FULL, ICON }

data class VideoExportRequest(
    val projectId: String,
    val preset: VideoExportPreset,
    val logoVariant: ExportLogoVariant = ExportLogoVariant.FULL,
    val use720p: Boolean = false,
    val burnCaptions: Boolean = false,
    val showProgressBar: Boolean = false,
    val backgroundMusicUri: String? = null,
    val musicVolume: Float = 0.20f,
    val musicLoop: Boolean = true,
    val autoDuckMusic: Boolean = true,
    val logoPlacementMode: LogoPlacementMode = LogoPlacementMode.SAFE
)

data class PreparedVideoExport(
    val composition: Composition,
    val outputFile: File,
    val totalDurationMs: Long,
    val missingVoiceFiles: List<String>,
    val tempFiles: List<File>
)

class VideoExportPreparer(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun prepare(request: VideoExportRequest): PreparedVideoExport {
        val app = context.applicationContext as com.sdtech.studio.SDStudioApplication
        val container = app.appContainer
        val project = runBlocking { container.projectRepository.getProject(request.projectId) }
            ?: error("The video project could not be found.")
        require(project.type == ProjectType.VIDEO) { "This project is not a video project." }
        val payload = json.decodeFromString<VideoProjectPayload>(project.payloadJson)
        require(payload.scenes.isNotEmpty()) { "Add at least one scene before exporting." }
        require(payload.scenes.none { it.backgroundType == SceneBackgroundType.VIDEO_CLIP && it.backgroundUri.isNullOrBlank() }) {
            "A scene is set to Video Clip but has no selected clip. Choose the clip or change its background type."
        }

        val brand = runBlocking { container.brandKitRepository.brandKit.first() }
        val logos = runBlocking { container.brandKitRepository.logoAssets.first() }
        val preset = request.preset
        val width = if (request.use720p) preset.fallbackWidth else preset.width
        val height = if (request.use720p) preset.fallbackHeight else preset.height
        val ratio = preset.ratio
        val templates = TemplateRepository(context).loadSceneTemplates().associateBy { it.id }
        val cacheDir = File(context.cacheDir, "video-export-${request.projectId}").apply {
            deleteRecursively()
            if (!mkdirs() && !isDirectory) throw java.io.IOException("Cannot create temporary export storage. Check available device storage.")
        }
        val tempFiles = mutableListOf<File>()
        logExportMemory("prepare-start")
        val missingVoices = mutableListOf<String>()
        val clipEndPositions = payload.scenes.map { scene ->
            if (scene.backgroundType != SceneBackgroundType.VIDEO_CLIP || scene.backgroundUri.isNullOrBlank()) null
            else {
                val trimStart = scene.clipTrimStartMs.coerceAtLeast(0L)
                val fallbackDuration = scene.manualDurationMs ?: scene.voiceDurationMs?.plus(400L) ?: 3_000L
                val requestedEnd = scene.clipTrimEndMs?.takeIf { it > trimStart } ?: (trimStart + fallbackDuration)
                val sourceDuration = readVideoDurationMs(Uri.parse(scene.backgroundUri))
                minOf(requestedEnd, sourceDuration).also {
                    require(it > trimStart) { "Scene video clip ${scene.id} has an invalid trim range or is too short." }
                }
            }
        }
        val durations = payload.scenes.mapIndexed { index, scene ->
            val clipEnd = clipEndPositions[index]
            if (clipEnd != null) (clipEnd - scene.clipTrimStartMs.coerceAtLeast(0L)).coerceAtLeast(1L)
            else (scene.manualDurationMs ?: scene.voiceDurationMs?.plus(400L) ?: 3_000L).coerceAtLeast(500L)
        }
        val totalDuration = durations.sum()
        val estimatedBytesPerSecond = if (request.use720p) 850_000.0 else 1_650_000.0
        val estimatedOutputBytes = (totalDuration / 1_000.0 * estimatedBytesPerSecond).toLong()
        val requiredFreeBytes = estimatedOutputBytes * 2L + 120L * 1024L * 1024L
        if (cacheDir.usableSpace < requiredFreeBytes) throw java.io.IOException("Not enough free storage for this export. Free up space or choose 720p.")
        val videoSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
        val audioSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        val anyVoiceFile = payload.scenes.any { scene ->
            listOfNotNull(scene.voiceFilePath, scene.voiceOriginalFilePath)
                .any { it.isNotBlank() && File(it).isFile }
        }
        val firstSceneHasVoice = payload.scenes.firstOrNull()?.let { scene ->
            listOfNotNull(scene.voiceFilePath, scene.voiceOriginalFilePath)
                .any { it.isNotBlank() && File(it).isFile }
        } == true
        // Media3 1.11.1 requires this flag when the audio sequence starts with a gap.
        if (anyVoiceFile && !firstSceneHasVoice) {
            audioSequence.experimentalSetForceAudioTrack(true)
        }
        var audioCursor = 0L

        payload.scenes.forEachIndexed { index, scene ->
            logExportMemory("scene-${index + 1}-start")
            val duration = durations[index]
            if (scene.backgroundType == SceneBackgroundType.VIDEO_CLIP && !scene.backgroundUri.isNullOrBlank()) {
                val uri = Uri.parse(scene.backgroundUri)
                val trimStart = scene.clipTrimStartMs.coerceAtLeast(0L)
                val trimEnd = clipEndPositions[index] ?: (trimStart + duration)
                val media = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(trimStart)
                            .setEndPositionMs(trimEnd)
                            .build()
                    ).build()
                val presentation = Presentation.createForWidthAndHeight(
                    width, height, Presentation.LAYOUT_SCALE_TO_FIT
                )
                val item = EditedMediaItem.Builder(media)
                    .setRemoveAudio(true)
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), listOf(presentation)))
                    .build()
                videoSequence.addItem(item)
            } else {
                val templateId = when {
                    scene.type == SceneType.OUTRO -> "scene_outro"
                    scene.textStyle == SceneTextStyle.BULLET_POINT -> "scene_bullet"
                    else -> "scene_statement"
                }
                val template = templates[templateId] ?: error("Missing scene template: $templateId")
                val source = scene.backgroundUri?.takeIf {
                    scene.backgroundType == SceneBackgroundType.IMAGE
                }?.let { uriString ->
                    runCatching { decodeBoundedUri(Uri.parse(uriString), width, height) }.getOrNull()
                }
                if (scene.backgroundType == SceneBackgroundType.IMAGE && source == null) {
                    throw IllegalArgumentException("Scene ${index + 1} image is missing or no longer accessible. Re-select the background image and try again.")
                }
                try {
                    val bg = template.layers.filterIsInstance<TemplateLayer.Background>().firstOrNull()
                    val adjusted = if (bg == null) template else {
                        val replacement = when (scene.backgroundType) {
                            SceneBackgroundType.BRAND_COLOR -> bg.copy(kind = BackgroundKind.SOLID,
                                color = brand?.palette?.firstOrNull()?.hex ?: "#102B35")
                            SceneBackgroundType.GRADIENT -> bg.copy(kind = BackgroundKind.GRADIENT,
                                color = "#102B35", endColor = "#159B98")
                            SceneBackgroundType.IMAGE -> bg.copy(kind = BackgroundKind.IMAGE, imageAsset = "scene_bg")
                            SceneBackgroundType.VIDEO_CLIP -> bg.copy(kind = BackgroundKind.SOLID, color = "#102B35")
                        }
                        template.copy(layers = template.layers.map { if (it.id == bg.id) replacement else it })
                    }.let { t -> t.copy(layers = t.layers.filterNot { it is TemplateLayer.LogoSlot }) }

                    val values = sceneValues(scene, brand)
                    val rendered = TemplateRenderer(context).render(
                        adjusted,
                        TemplateContent(values = values, images = if (source != null) mapOf("scene_bg" to source) else emptyMap(), brandLock = false),
                        ratio,
                        theme = adjusted.themes.firstOrNull(),
                        options = RenderOptions(logoPlacementMode = request.logoPlacementMode,
                            brandColors = brand?.palette.orEmpty().associate { it.name.lowercase() to it.hex }),
                        brandKit = brand,
                        logos = emptyList()
                    )
                    val bitmap = if (rendered.width == width && rendered.height == height) rendered else
                        Bitmap.createScaledBitmap(rendered, width, height, true).also { rendered.recycle() }
                    val file = File(cacheDir, "scene-$index.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    logExportMemory("scene-${index + 1}-rendered")
                    tempFiles += file
                    val media = MediaItem.Builder().setUri(Uri.fromFile(file)).setImageDurationMs(duration).build()
                    videoSequence.addItem(EditedMediaItem.Builder(media).setFrameRate(30).build())
                } finally {
                    source?.takeIf { !it.isRecycled }?.recycle()
                }
            }

            val voiceFile = listOfNotNull(scene.voiceFilePath, scene.voiceOriginalFilePath)
                .filter(String::isNotBlank).map(::File).firstOrNull { it.isFile }
            val hasVoiceReference = !scene.voiceFilePath.isNullOrBlank() || !scene.voiceOriginalFilePath.isNullOrBlank()
            if (hasVoiceReference && voiceFile == null) missingVoices += "Scene ${index + 1}"
            if (anyVoiceFile) {
                if (voiceFile != null && voiceFile.isFile) {
                    if (audioCursor < indexStart(durations, index)) {
                        audioSequence.addGap((indexStart(durations, index) - audioCursor) * 1_000L)
                        audioCursor = indexStart(durations, index)
                    }
                    val voiceDuration = scene.voiceDurationMs?.takeIf { it > 0 } ?: duration
                    val clipDuration = minOf(voiceDuration, duration)
                    val audioItem = MediaItem.Builder().setUri(Uri.fromFile(voiceFile))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0)
                                .setEndPositionMs(clipDuration)
                                .build()
                        ).build()
                    audioSequence.addItem(EditedMediaItem.Builder(audioItem).build())
                    audioCursor += clipDuration
                    if (audioCursor < indexStart(durations, index) + duration) {
                        audioSequence.addGap((indexStart(durations, index) + duration - audioCursor) * 1_000L)
                        audioCursor = indexStart(durations, index) + duration
                    }
                } else {
                    val target = indexStart(durations, index) + duration
                    if (audioCursor < target) {
                        audioSequence.addGap((target - audioCursor) * 1_000L)
                        audioCursor = target
                    }
                }
            }
        }

        if (!anyVoiceFile) {
            val silence = File(cacheDir, "silence.wav")
            writeSilentWav(silence, totalDuration)
            tempFiles += silence
            audioSequence.addItem(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silence))).build())
        }

        // Optional background music is built as short, scene-aligned audio items. This lets
        // each scene's music segment use a stable gain value (ducked when that scene has VO).
        val musicSequence = request.backgroundMusicUri?.takeIf { it.isNotBlank() }?.let { musicUriString ->
            val musicUri = Uri.parse(musicUriString)
            val sourceDuration = readAudioDurationMs(musicUri)
            require(sourceDuration > 0L) { "The selected background music file has no readable duration." }
            val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            val loopMusic = request.musicLoop
            val musicGain = request.musicVolume.coerceIn(0f, 1f)
            var sourceCursorMs = 0L
            payload.scenes.forEachIndexed { index, scene ->
                var remainingMs = durations[index]
                val voiceExists = listOfNotNull(scene.voiceFilePath, scene.voiceOriginalFilePath)
                    .filter(String::isNotBlank).map(::File).any { it.isFile }
                val gain = if (request.autoDuckMusic && voiceExists) musicGain * 0.22f else musicGain
                while (remainingMs > 0L && (loopMusic || sourceCursorMs < sourceDuration)) {
                    val sourcePosition = if (loopMusic) sourceCursorMs % sourceDuration else sourceCursorMs
                    val chunkMs = minOf(remainingMs, sourceDuration - sourcePosition)
                    if (chunkMs <= 0L) break
                    val media = MediaItem.Builder().setUri(musicUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(sourcePosition)
                                .setEndPositionMs(sourcePosition + chunkMs)
                                .build()
                        ).build()
                    val channelMixer = ChannelMixingAudioProcessor().apply {
                        for (channels in 1..6) {
                            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(channels, 1))
                        }
                    }
                    val gainProcessor = GainProcessor(DefaultGainProvider.Builder(gain).build())
                    sequence.addItem(EditedMediaItem.Builder(media)
                        .setRemoveVideo(true)
                        .setEffects(Effects(listOf(channelMixer, gainProcessor), emptyList()))
                        .build())
                    remainingMs -= chunkMs
                    sourceCursorMs += chunkMs
                }
                // If music is not looping and has ended, the sequence simply ends; the video
                // and voice-over tracks continue unaffected.
            }
            sequence.build()
        }

        val logo = logos.lastOrNull { it.type.name == request.logoVariant.name }
            ?: if (request.logoVariant == ExportLogoVariant.ICON) logos.lastOrNull { it.type.name == "FULL" } else null
        val overlays = mutableListOf<androidx.media3.effect.TextureOverlay>()
        if (logo != null) {
            val original = decodeBoundedFile(logo.filePath, width, height)
            if (original != null) {
                val profile = RatioProfile.forRatio(ratio)
                val placement = LogoPlacementRules.forRatio(ratio, request.logoPlacementMode,
                    original.width.toFloat() / original.height)
                val scaleX = width.toFloat() / profile.width
                val scaleY = height.toFloat() / profile.height
                val logoWidth = (placement.width * scaleX).roundToInt().coerceAtLeast(1)
                val logoHeight = (placement.height * scaleY).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(original, logoWidth, logoHeight, true)
                if (scaled !== original) original.recycle()
                val rightEdge = placement.x * scaleX + logoWidth
                val topEdge = placement.y * scaleY
                val bgAnchorX = (2f * rightEdge / width) - 1f
                val bgAnchorY = 1f - (2f * topEdge / height)
                val settings = StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(bgAnchorX, bgAnchorY)
                    .setOverlayFrameAnchor(-1f, -1f)
                    .build()
                overlays += BitmapOverlay.createStaticBitmapOverlay(scaled, settings)
            }
        }
        if (request.burnCaptions || request.showProgressBar) {
            val captionOverlay = ExportPolishOverlay(
                width = width,
                height = height,
                scenes = payload.scenes.map { it.text },
                durationsMs = durations,
                brandAccent = brand?.palette?.getOrNull(1)?.hex ?: "#159B98",
                captionsEnabled = request.burnCaptions,
                progressEnabled = request.showProgressBar
            )
            captionOverlay.setCanvasSize(width, height)
            overlays += captionOverlay
        }
        val compositionEffects = if (overlays.isNotEmpty()) listOf(OverlayEffect(overlays)) else emptyList()

        val videoSeq = videoSequence.build()
        val audioSeq = audioSequence.build()
        val sequences = buildList {
            add(videoSeq)
            add(audioSeq)
            musicSequence?.let(::add)
        }
        val composition = Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), compositionEffects))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        val output = File(context.cacheDir, "sdstudio-${request.projectId}-${System.currentTimeMillis()}.mp4")
        return PreparedVideoExport(composition, output, totalDuration, missingVoices, tempFiles)
    }

    private fun readAudioDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (error: Throwable) {
            throw IllegalArgumentException("Cannot read the selected music file. Choose a supported audio file and try again.", error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun logExportMemory(stage: String) {
        runCatching {
            val info = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(info)
            android.util.Log.i("SDStudioExportMemory", "stage=$stage pssKb=${info.totalPss} javaHeapCommittedKb=${Runtime.getRuntime().totalMemory() / 1024L}")
        }
    }

    private fun decodeBoundedUri(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }
    }

    private fun decodeBoundedFile(path: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, maxWidth: Int, maxHeight: Int): Int {
        var sample = 1
        while (sourceWidth / sample > maxWidth || sourceHeight / sample > maxHeight) sample *= 2
        return sample
    }

    private fun readVideoDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: error("Could not read the duration of video clip $uri.")
        } catch (error: Throwable) {
            throw IllegalArgumentException("Cannot read the selected video clip. Re-select it and check file access.", error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeSilentWav(file: File, durationMs: Long) {
        val sampleRate = 44_100
        val channels = 1
        val bitsPerSample = 16
        val dataBytes = (durationMs.coerceAtLeast(500L) * sampleRate * channels * bitsPerSample / 8_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        file.outputStream().buffered().use { out ->
            fun writeInt(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff); out.write((value shr 16) and 0xff); out.write((value shr 24) and 0xff) }
            fun writeShort(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff) }
            out.write("RIFF".toByteArray()); writeInt(36 + dataBytes); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); writeInt(16); writeShort(1); writeShort(channels)
            writeInt(sampleRate); writeInt(sampleRate * channels * bitsPerSample / 8)
            writeShort(channels * bitsPerSample / 8); writeShort(bitsPerSample)
            out.write("data".toByteArray()); writeInt(dataBytes)
            val zeros = ByteArray(16 * 1024)
            var remaining = dataBytes
            while (remaining > 0) { val count = minOf(remaining, zeros.size); out.write(zeros, 0, count); remaining -= count }
        }
    }

    private fun indexStart(durations: List<Long>, index: Int): Long = durations.take(index).sum()

    private fun sceneValues(scene: Scene, brand: BrandKit?): Map<String, String> {
        val safeText = scene.text.take(1_200)
        return when (scene.type) {
        SceneType.OUTRO -> mapOf(
            "headline" to safeText.lineSequence().firstOrNull().orEmpty(),
            "cta" to safeText.lineSequence().drop(1).joinToString(" ").ifBlank { brand?.defaultCta?.takeIf(String::isNotBlank) ?: "FOLLOW FOR MORE" },
            "monogram" to (brand?.name?.take(2)?.uppercase() ?: "KA")
        )
        SceneType.INTRO -> mapOf(
            "label" to (brand?.name ?: "INTRODUCTION").uppercase(), "keyword" to "",
            "headline" to safeText.lineSequence().firstOrNull().orEmpty(),
            "footer" to safeText.lineSequence().drop(1).joinToString(" ").ifBlank { brand?.slogan?.takeIf(String::isNotBlank) ?: "Your brand. Your story." }
        )
        SceneType.CONTENT -> {
            val words = safeText.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val highlighted = scene.textStyle == SceneTextStyle.HIGHLIGHTED_KEYWORD && words.isNotEmpty()
            mapOf("label" to "THE KEY IDEA", "keyword" to if (highlighted) words.first() else "",
                "headline" to when {
                    scene.textStyle == SceneTextStyle.BULLET_POINT -> "• $safeText"
                    highlighted -> words.drop(1).joinToString(" ")
                    else -> safeText
                }, "footer" to "")
        }
        }
    }
}

class VideoExportRunner(
    private val context: Context,
    private val onProgress: (Int) -> Unit,
    private val onComplete: (File, Long) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var transformer: Transformer? = null
    fun cancel() { cancelled.set(true); runCatching { transformer?.cancel() } }

    fun start(prepared: PreparedVideoExport) {
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: androidx.media3.transformer.ExportResult) {
                if (cancelled.get()) return
                onProgress(100)
                onComplete(prepared.outputFile, prepared.totalDurationMs)
            }
            override fun onError(
                composition: Composition,
                exportResult: androidx.media3.transformer.ExportResult,
                exportException: androidx.media3.transformer.ExportException
            ) {
                onError(exportException)
            }
        }
        val instance = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        transformer = instance
        instance.start(prepared.composition, prepared.outputFile.absolutePath)
    }
}
