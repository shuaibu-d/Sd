package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.media.BindPlayerHolderLifecycle
import com.sdtech.studio.media.Media3FrameExtractor
import com.sdtech.studio.render.*
import com.sdtech.studio.video.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val PREVIEW_WIDTH = 540
private const val DEFAULT_SCENE_MS = 3_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPreviewScreen(projectId: String, onBack: () -> Unit = {}, onExport: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { (context.applicationContext as SDStudioApplication).appContainer }
    val playerHolder = app.playerHolder
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    val brandKit by app.brandKitRepository.brandKit.collectAsState(initial = null)
    val logos by app.brandKitRepository.logoAssets.collectAsState(initial = emptyList())

    var title by remember { mutableStateOf("Explainer video") }
    var ratio by remember { mutableStateOf("9:16") }
    var payload by remember { mutableStateOf<VideoProjectPayload?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var previewBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    val scenes = payload?.scenes.orEmpty()
    fun sceneDuration(scene: Scene): Long = VideoDurationCalculator.sceneDurationMs(scene, DEFAULT_SCENE_MS, 500L)
    val durations = remember(scenes) { scenes.map(::sceneDuration) }
    val totalDuration = durations.sum()
    val currentDuration = durations.getOrNull(currentIndex) ?: 0L
    val globalPosition = durations.take(currentIndex).sum() + positionMs.coerceIn(0L, currentDuration)
    val currentScene = scenes.getOrNull(currentIndex)

    fun stopAudio() {
        playerHolder.stop()
    }

    fun pausePreview() {
        playbackJob?.cancel(); playbackJob = null
        playerHolder.pause()
        playing = false
    }

    fun startSceneVoice(scene: Scene, offsetMs: Long) {
        stopAudio()
        val path = scene.voiceFilePath?.takeIf { it.isNotBlank() && File(it).isFile } ?: return
        playerHolder.playFile(
            file = File(path),
            startPositionMs = offsetMs.coerceAtLeast(0L),
            isSpeech = true
        )
    }

    fun startPreview(index: Int = currentIndex, offsetMs: Long = positionMs) {
        if (scenes.isEmpty()) return
        playbackJob?.cancel()
        stopAudio()
        var safeIndex = index.coerceIn(0, scenes.lastIndex)
        var safeOffset = offsetMs
        if (safeIndex == scenes.lastIndex && safeOffset >= (durations.lastOrNull() ?: DEFAULT_SCENE_MS)) {
            safeIndex = 0
            safeOffset = 0L
        }
        currentIndex = safeIndex
        positionMs = safeOffset.coerceIn(0L, (durations.getOrNull(safeIndex) ?: DEFAULT_SCENE_MS) - 1L)
        playing = true
        playbackJob = scope.launch {
            var sceneIndex = safeIndex
            var startOffset = positionMs
            while (sceneIndex < scenes.size && playing) {
                currentIndex = sceneIndex
                val duration = durations.getOrNull(sceneIndex) ?: DEFAULT_SCENE_MS
                val scene = scenes[sceneIndex]
                startSceneVoice(scene, startOffset)
                val startedAt = android.os.SystemClock.elapsedRealtime()
                while (playing) {
                    val elapsed = startOffset + (android.os.SystemClock.elapsedRealtime() - startedAt)
                    positionMs = elapsed.coerceAtMost(duration)
                    if (elapsed >= duration) break
                    delay(60)
                }
                stopAudio()
                if (!playing) break
                sceneIndex++
                startOffset = 0L
                if (sceneIndex < scenes.size) positionMs = 0L
            }
            if (sceneIndex >= scenes.size) {
                playing = false
                currentIndex = scenes.lastIndex.coerceAtLeast(0)
                positionMs = durations.lastOrNull() ?: 0L
            }
        }
    }

    LaunchedEffect(projectId) {
        loading = true
        val project = app.projectRepository.getProject(projectId)
        if (project != null && project.type == ProjectType.VIDEO) {
            title = project.title
            ratio = project.ratio
            payload = runCatching {
                com.sdtech.studio.video.domain.ProjectDocumentMigrator.decodeAsLegacyPayload(
                    projectId = project.id,
                    canvasRatio = project.ratio,
                    payloadJson = project.payloadJson
                )
            }.getOrNull()
        }
        loading = false
    }

    var renderRetryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(payload, brandKit, logos, ratio, renderRetryToken) {
        val current = payload ?: return@LaunchedEffect
        if (current.scenes.isEmpty()) return@LaunchedEffect
        renderError = null
        val rendered = com.sdtech.studio.performance.ErrorBoundary.runGuarded(
            source = "QuickPreview:$projectId",
            title = "Scene preview issue",
            message = "Could not render the video scene previews. Tap Retry to try again.",
            onRetry = { renderRetryToken++ }
        ) {
            com.sdtech.studio.performance.RenderDispatcher.withRenderContext {
                val templates = TemplateRepository(context).loadSceneTemplates().associateBy { it.id }
                val result = linkedMapOf<String, Bitmap>()
                val previewMaxEdge = (PREVIEW_WIDTH * com.sdtech.studio.performance.ThermalGuard.effectivePreviewScale)
                    .roundToInt()
                    .coerceAtLeast(180)
                current.scenes.forEach { scene ->
                    val cacheKey = "quick_preview:$projectId:${scene.id}:$ratio:${scene.hashCode()}:$previewMaxEdge"
                    val cachedPreview = com.sdtech.studio.performance.CentralBitmapPool.get(cacheKey)
                    if (cachedPreview != null) {
                        result[scene.id] = cachedPreview
                        return@forEach
                    }
                    val templateId = when {
                        scene.type == SceneType.OUTRO -> "scene_outro"
                        scene.textStyle == SceneTextStyle.BULLET_POINT -> "scene_bullet"
                        else -> "scene_statement"
                    }
                    val original = templates[templateId] ?: return@forEach
                    var sourceImage: Bitmap? = null
                    try {
                        val uriString = scene.backgroundUri
                        if (!uriString.isNullOrBlank() && (scene.backgroundType == SceneBackgroundType.IMAGE || scene.backgroundType == SceneBackgroundType.VIDEO_CLIP)) {
                            sourceImage = runCatching {
                                val uri = Uri.parse(uriString)
                                if (scene.backgroundType == SceneBackgroundType.VIDEO_CLIP) {
                                    Media3FrameExtractor.extractFrameCached(
                                        context = context,
                                        uri = uri,
                                        positionMs = scene.clipTrimStartMs.coerceAtLeast(0L),
                                        maxEdgePx = previewMaxEdge
                                    )
                                } else {
                                    context.contentResolver.openInputStream(uri)?.use {
                                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                                    }
                                }
                            }.getOrNull()
                        }
                        val bg = original.layers.filterIsInstance<TemplateLayer.Background>().firstOrNull()
                        val withBackground = if (bg == null) original else {
                            val replacement = when (scene.backgroundType) {
                                SceneBackgroundType.BRAND_COLOR -> bg.copy(kind = BackgroundKind.SOLID, color = brandKit?.palette?.firstOrNull()?.hex ?: "#101C2C")
                                SceneBackgroundType.GRADIENT -> bg.copy(kind = BackgroundKind.GRADIENT, color = "#101C2C", endColor = "#16A6A0")
                                SceneBackgroundType.IMAGE, SceneBackgroundType.VIDEO_CLIP -> bg.copy(kind = BackgroundKind.IMAGE, imageAsset = "scene_bg")
                            }
                            original.copy(layers = original.layers.map { if (it.id == bg.id) replacement else it })
                        }
                        val withOverlay = if (scene.backgroundType == SceneBackgroundType.IMAGE || scene.backgroundType == SceneBackgroundType.VIDEO_CLIP) {
                            val overlay = TemplateLayer.Shape("preview_image_overlay", 0f, 0f, 100f, 100f, ShapeKind.RECTANGLE, "#66101C2C")
                            val firstText = withBackground.layers.indexOfFirst { it is TemplateLayer.Text }.let { if (it < 0) withBackground.layers.size else it }
                            withBackground.copy(layers = withBackground.layers.toMutableList().also { it.add(firstText, overlay) })
                        } else withBackground
                        val values = when (scene.type) {
                            SceneType.OUTRO -> mapOf("headline" to scene.text.lineSequence().firstOrNull().orEmpty(), "cta" to scene.text.lineSequence().drop(1).joinToString(" ").ifBlank { brandKit?.defaultCta?.takeIf(String::isNotBlank) ?: "FOLLOW FOR MORE" }, "monogram" to (brandKit?.name?.take(2)?.uppercase() ?: "SD"))
                            SceneType.INTRO -> mapOf("label" to (brandKit?.name ?: "INTRODUCTION").uppercase(), "keyword" to "", "headline" to scene.text.lineSequence().firstOrNull().orEmpty(), "footer" to scene.text.lineSequence().drop(1).joinToString(" ").ifBlank { brandKit?.slogan?.takeIf(String::isNotBlank) ?: "Your brand. Your story." })
                            SceneType.CONTENT -> {
                                val words = scene.text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                                val highlighted = scene.textStyle == SceneTextStyle.HIGHLIGHTED_KEYWORD && words.isNotEmpty()
                                mapOf("label" to "THE KEY IDEA", "keyword" to if (highlighted) words.first() else "", "headline" to when {
                                    scene.textStyle == SceneTextStyle.BULLET_POINT -> "• ${scene.text}"
                                    highlighted -> words.drop(1).joinToString(" ")
                                    else -> scene.text
                                }, "footer" to "")
                            }
                        }
                        val previewBmp = com.sdtech.studio.performance.CentralBitmapPool.getOrPut(cacheKey) {
                            TemplateRenderer(context).render(
                                withOverlay,
                                TemplateContent(values = values, images = if (sourceImage != null) mapOf("scene_bg" to sourceImage!!) else emptyMap(), brandLock = false),
                                ratio,
                                theme = withOverlay.themes.firstOrNull(),
                                options = RenderOptions(
                                    logoPlacementMode = LogoPlacementMode.SAFE,
                                    showSafeZones = false,
                                    brandColors = brandKit?.palette.orEmpty().associate { it.name.lowercase() to it.hex },
                                    maxDimension = previewMaxEdge
                                ),
                                brandKit = brandKit,
                                logos = logos
                            )
                        }
                        if (previewBmp != null) {
                            result[scene.id] = previewBmp
                        }
                    } catch (_: Throwable) {
                        // A bad/missing background asset should not prevent other scenes from rendering.
                    }
                }
                result
            }
        }.orEmpty()
        previewBitmaps = rendered
        if (rendered.isEmpty()) renderError = "Scene previews could not be rendered. Check the scene templates and media files."
    }

    BindPlayerHolderLifecycle(
        playerHolder = playerHolder,
        onStop = {
            playbackJob?.cancel()
            playbackJob = null
            playing = false
        }
    )

    fun jumpTo(index: Int) {
        val wasPlaying = playing
        playbackJob?.cancel(); playbackJob = null; stopAudio()
        currentIndex = index.coerceIn(0, scenes.lastIndex)
        positionMs = 0L
        playing = false
        if (wasPlaying) startPreview(currentIndex, 0L)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Column { Text("Quick preview"); Text(title, style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }, actions = { TextButton(onClick = { onExport(projectId) }, enabled = !loading && scenes.isNotEmpty()) { Text("Export") } })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (scenes.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("This project has no scenes yet.") }
            } else {
                val bitmap = currentScene?.let { previewBitmaps[it.id] }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (bitmap != null && !bitmap.isRecycled) {
                        Image(bitmap.asImageBitmap(), contentDescription = "Scene ${currentIndex + 1} preview", modifier = Modifier.fillMaxHeight().fillMaxWidth().clip(RoundedCornerShape(14.dp)))
                    } else {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            if (renderError != null) Text(renderError!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
                        }
                    }
                    Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = .62f), shape = RoundedCornerShape(8.dp)) {
                        Text("SCENE ${currentIndex + 1} / ${scenes.size}", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${formatTime(totalDuration)} total", style = MaterialTheme.typography.labelLarge)
                }
                if (totalDuration > 60_000L) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠", color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("This video is over 60 seconds. It may exceed the recommended Reels length.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(scenes, key = { _, scene -> scene.id }) { index, scene ->
                        val fraction = (durations[index].toFloat() / totalDuration.coerceAtLeast(1L)).coerceIn(.08f, 1f)
                        Surface(
                            modifier = Modifier.width((220 * fraction).coerceAtLeast(44f).dp).height(38.dp).clickable { jumpTo(index) },
                            shape = RoundedCornerShape(7.dp),
                            color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text("${index + 1} · ${formatTime(durations[index])}", style = MaterialTheme.typography.labelSmall, color = if (index == currentIndex) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        }
                    }
                }
                Slider(
                    value = globalPosition.toFloat().coerceIn(0f, totalDuration.coerceAtLeast(1L).toFloat()),
                    onValueChange = { value ->
                        if (!wasPlayingBeforeScrub) wasPlayingBeforeScrub = playing
                        if (playing) pausePreview()
                        var remaining = value.toLong().coerceAtLeast(0L)
                        var target = 0
                        while (target < durations.lastIndex && remaining >= durations[target]) { remaining -= durations[target]; target++ }
                        currentIndex = target
                        positionMs = remaining.coerceIn(0L, (durations.getOrNull(target) ?: 1L))
                    },
                    onValueChangeFinished = {
                        if (wasPlayingBeforeScrub) startPreview(currentIndex, positionMs)
                        wasPlayingBeforeScrub = false
                    },
                    valueRange = 0f..totalDuration.coerceAtLeast(1L).toFloat()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(globalPosition), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("${currentIndex + 1}/${scenes.size}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(totalDuration), style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { jumpTo((currentIndex - 1).coerceAtLeast(0)) }, enabled = currentIndex > 0) { Text("Previous") }
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(onClick = { if (playing) pausePreview() else startPreview(currentIndex, positionMs) }, modifier = Modifier.size(58.dp)) {
                        Text(if (playing) "Ⅱ" else "▶", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { jumpTo((currentIndex + 1).coerceAtMost(scenes.lastIndex)) }, enabled = currentIndex < scenes.lastIndex) { Text("Next") }
                }
                Text("Preview only · Scene timing follows voice length + 0.4 seconds unless manually set.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
