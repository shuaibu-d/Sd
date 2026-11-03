package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.render.*
import com.sdtech.studio.video.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScriptEditorScreen(projectIdArg: String? = null, onBack: () -> Unit = {}, onOpenVoiceOver: (String) -> Unit = {}, onOpenPreview: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { (context.applicationContext as SDStudioApplication).appContainer }
    val scope = rememberCoroutineScope()
    var projectId by rememberSaveable { mutableStateOf(projectIdArg ?: UUID.randomUUID().toString()) }
    var ratio by rememberSaveable { mutableStateOf("9:16") }
    var projectTitle by rememberSaveable { mutableStateOf("Explainer video") }
    var script by rememberSaveable { mutableStateOf("") }
    var scenes by remember { mutableStateOf(listOf(
        Scene(type = SceneType.INTRO, text = "Your brand story starts here.\nIdeas that move people."),
        Scene(type = SceneType.OUTRO, text = "Follow for more.\nVisit our website or message us on WhatsApp.")
    )) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var projectCreated by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showScriptInput by remember { mutableStateOf(true) }
    var previewBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val brandKit by app.brandKitRepository.brandKit.collectAsState(initial = null)
    val logos by app.brandKitRepository.logoAssets.collectAsState(initial = emptyList())

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedIndex in scenes.indices) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scenes = scenes.toMutableList().also { list -> list[selectedIndex] = list[selectedIndex].copy(backgroundType = SceneBackgroundType.IMAGE, backgroundUri = uri.toString()) }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedIndex in scenes.indices) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scenes = scenes.toMutableList().also { list -> list[selectedIndex] = list[selectedIndex].copy(backgroundType = SceneBackgroundType.VIDEO_CLIP, backgroundUri = uri.toString()) }
        }
    }

    val payload = remember(scenes) { VideoProjectPayload(scenes = scenes, showIntro = scenes.any { it.type == SceneType.INTRO }, showOutro = scenes.any { it.type == SceneType.OUTRO }) }
    val json = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }

    suspend fun saveDraft() {
        isSaving = true
        val now = System.currentTimeMillis()
        app.projectRepository.saveProject(Project(
            id = projectId, type = ProjectType.VIDEO, title = projectTitle.ifBlank { "Explainer video" },
            ratio = ratio, status = ProjectStatus.DRAFT, payloadJson = json.encodeToString(payload),
            createdAt = app.projectRepository.getProject(projectId)?.createdAt ?: now, updatedAt = now
        ))
        projectCreated = true
        isSaving = false
    }

    LaunchedEffect(Unit) {
        val existing = runCatching { app.projectRepository.getProject(projectId) }.getOrNull()
        val restored = existing?.takeIf { it.type == ProjectType.VIDEO }?.let {
            runCatching { json.decodeFromString<VideoProjectPayload>(it.payloadJson) }.getOrNull()
        }
        if (restored != null) {
            scenes = restored.scenes
            projectTitle = existing!!.title
            ratio = existing!!.ratio
            projectCreated = true
            selectedIndex = 0
        } else {
            runCatching { ratio = app.settingsRepository.settings.first().lastUsedRatio }
            saveDraft()
        }
    }
    LaunchedEffect(projectTitle, ratio, scenes) {
        if (!projectCreated) return@LaunchedEffect
        delay(650)
        saveDraft()
    }

    LaunchedEffect(scenes, ratio, brandKit, logos) {
        if (scenes.isEmpty()) return@LaunchedEffect
        delay(220)
        val templates = withContext(Dispatchers.IO) { TemplateRepository(context).loadSceneTemplates().associateBy { it.id } }
        val rendered = withContext(Dispatchers.IO) {
            val output = mutableMapOf<String, Bitmap>()
            scenes.forEach { scene ->
                val templateId = when {
                    scene.type == SceneType.OUTRO -> "scene_outro"
                    scene.textStyle == SceneTextStyle.BULLET_POINT -> "scene_bullet"
                    else -> "scene_statement"
                }
                val original = templates[templateId] ?: return@forEach
                val image = scene.backgroundUri?.let { uriString ->
                    runCatching {
                        val uri = Uri.parse(uriString)
                        if (scene.backgroundType == SceneBackgroundType.VIDEO_CLIP) {
                            MediaMetadataRetriever().run {
                                setDataSource(context, uri)
                                val frame = getFrameAtTime(0)
                                release()
                                frame
                            }
                        } else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()
                }
                val bg = original.layers.filterIsInstance<TemplateLayer.Background>().firstOrNull()
                val adjustedBackground = if (bg != null) {
                    val next = when (scene.backgroundType) {
                        SceneBackgroundType.BRAND_COLOR -> bg.copy(kind = BackgroundKind.SOLID, color = brandKit?.palette?.firstOrNull()?.hex ?: "#101C2C")
                        SceneBackgroundType.GRADIENT -> bg.copy(kind = BackgroundKind.GRADIENT, color = "#101C2C", endColor = "#16A6A0")
                        SceneBackgroundType.IMAGE, SceneBackgroundType.VIDEO_CLIP -> bg.copy(kind = BackgroundKind.IMAGE, imageAsset = "scene_bg")
                    }
                    original.copy(layers = original.layers.map { if (it.id == bg.id) next else it })
                } else original
                val withImageOverlay = if (scene.backgroundType == SceneBackgroundType.IMAGE || scene.backgroundType == SceneBackgroundType.VIDEO_CLIP) {
                    val overlay = TemplateLayer.Shape(id = "scene_image_overlay", x = 0f, y = 0f, width = 100f, height = 100f,
                        shape = ShapeKind.RECTANGLE, color = "#66101C2C")
                    val firstText = adjustedBackground.layers.indexOfFirst { it is TemplateLayer.Text }.let { if (it < 0) adjustedBackground.layers.size else it }
                    adjustedBackground.copy(layers = adjustedBackground.layers.toMutableList().also { it.add(firstText, overlay) })
                } else adjustedBackground
                val adjusted = withImageOverlay
                val values = when (scene.type) {
                    SceneType.OUTRO -> mapOf("headline" to scene.text.lineSequence().firstOrNull().orEmpty(), "cta" to scene.text.lineSequence().drop(1).joinToString(" ").ifBlank { brandKit?.defaultCta?.takeIf { it.isNotBlank() } ?: "FOLLOW FOR MORE" }, "monogram" to (brandKit?.name?.take(2)?.uppercase() ?: "SD"))
                    SceneType.INTRO -> mapOf("label" to (brandKit?.name ?: "INTRODUCTION").uppercase(), "keyword" to "", "headline" to scene.text.lineSequence().firstOrNull().orEmpty(), "footer" to scene.text.lineSequence().drop(1).joinToString(" ").ifBlank { brandKit?.slogan?.takeIf { it.isNotBlank() } ?: "Your brand. Your story." })
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
                val bitmap = runCatching {
                    TemplateRenderer(context).render(
                        adjusted, TemplateContent(values = values, images = if (image != null) mapOf("scene_bg" to image) else emptyMap(), brandLock = false),
                        ratio, theme = adjusted.themes.firstOrNull(),
                        options = RenderOptions(logoPlacementMode = LogoPlacementMode.SAFE, brandColors = brandKit?.palette.orEmpty().associate { it.name.lowercase() to it.hex }),
                        brandKit = brandKit, logos = logos
                    )
                }.getOrNull()
                image?.recycle()
                if (bitmap != null) output[scene.id] = Bitmap.createScaledBitmap(bitmap, 220, (220 * RatioProfile.forRatio(ratio).height.toFloat() / RatioProfile.forRatio(ratio).width).toInt(), true).also { if (it !== bitmap) bitmap.recycle() }
            }
            output
        }
        previewBitmaps = rendered
    }


    val selected = scenes.getOrNull(selectedIndex)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Explainer video"); Text(if (isSaving) "Saving draft…" else "Saved on this device", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { TextButton(onClick = { scope.launch { saveDraft(); onBack() } }) { Text("Back") } },
                actions = { TextButton(onClick = { scope.launch { saveDraft(); onBack() } }) { Text("Done") } }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { if (selectedIndex in scenes.indices) scenes = scenes.toMutableList().also { it.removeAt(selectedIndex) }; selectedIndex = (selectedIndex - 1).coerceAtLeast(0) }, enabled = selected != null, modifier = Modifier.weight(1f)) { Text("Delete") }
                    Button(onClick = {
                        if (scenes.size < ScriptSceneSplitter.MAX_SCENES) {
                            scenes = scenes.toMutableList().also { it.add((selectedIndex + 1).coerceAtMost(it.size), Scene()) }
                            selectedIndex = (selectedIndex + 1).coerceAtMost(scenes.lastIndex)
                        } else toastMessage = "This project has reached the ${ScriptSceneSplitter.MAX_SCENES}-scene limit."
                    }, enabled = scenes.size < ScriptSceneSplitter.MAX_SCENES, modifier = Modifier.weight(1f)) { Text("Add scene") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(projectTitle, { projectTitle = it.take(120) }, label = { Text("Video title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Video ratio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "4:5", "1:1", "16:9").forEach { value ->
                    FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(value) })
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Script", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showScriptInput = !showScriptInput }) { Text(if (showScriptInput) "Hide" else "Paste script") }
                    }
                    if (showScriptInput) {
                        OutlinedTextField(script, { script = it.take(10_000) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), placeholder = { Text("Paste or type your explainer script… Separate ideas with blank lines or sentences.") })
                        Button(onClick = {
                            val splitScenes = ScriptSceneSplitter.split(script)
                            val contentScenes = splitScenes.take(ScriptSceneSplitter.MAX_SCENES - 2).map { Scene(type = SceneType.CONTENT, text = it) }
                            if (contentScenes.isNotEmpty()) {
                                if (splitScenes.size > contentScenes.size) toastMessage = "The script was limited to ${ScriptSceneSplitter.MAX_SCENES - 2} content scenes to protect device memory."
                                val intro = scenes.firstOrNull { it.type == SceneType.INTRO } ?: Scene(type = SceneType.INTRO, text = "Your brand story starts here.\nIdeas that move people.")
                                val outro = scenes.firstOrNull { it.type == SceneType.OUTRO } ?: Scene(type = SceneType.OUTRO, text = "Follow for more.\nVisit our website or message us on WhatsApp.")
                                scenes = listOf(intro) + contentScenes + listOf(outro)
                                selectedIndex = 1.coerceAtMost(scenes.lastIndex)
                            }
                        }, enabled = script.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Split script into scenes") }
                        Text("Auto-split uses blank lines and sentence endings, then limits each scene to about 18 words.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scenes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${scenes.size} total", style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { saveDraft(); onOpenPreview(projectId) } }, modifier = Modifier.weight(1f)) { Text("Quick preview") }
                Button(onClick = { scope.launch { saveDraft(); onOpenVoiceOver(projectId) } }, modifier = Modifier.weight(1f)) { Text("Record voice-over") }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(scenes, key = { _, scene -> scene.id }) { index, scene ->
                    Card(
                        modifier = Modifier.width(112.dp).pointerInput(scene.id, index, scenes.size) {
                            var drag = 0f
                            detectDragGesturesAfterLongPress(onDrag = { change, amount ->
                                change.consume(); drag += amount.x
                                if (kotlin.math.abs(drag) > 42f) {
                                    val target = (index + if (drag > 0) 1 else -1).coerceIn(0, scenes.lastIndex)
                                    if (target != index) { scenes = scenes.toMutableList().also { val moved = it.removeAt(index); it.add(target, moved) }; selectedIndex = target; drag = 0f }
                                }
                            })
                        }.clickable { selectedIndex = index },
                        colors = CardDefaults.cardColors(containerColor = if (selectedIndex == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            previewBitmaps[scene.id]?.let { Image(it.asImageBitmap(), "Scene ${index + 1} thumbnail", Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(8.dp))) }
                                ?: Box(Modifier.fillMaxWidth().height(112.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("${index + 1}") }
                            Text("${index + 1}. ${scene.type.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text("${ScriptSceneSplitter.wordCount(scene.text)} words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (selected != null) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Edit scene ${selectedIndex + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(SceneType.INTRO, SceneType.CONTENT, SceneType.OUTRO).forEach { type ->
                                FilterChip(selected = selected.type == type, onClick = { updateScene(scenes, selectedIndex) { it.copy(type = type) }.also { scenes = it } }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        OutlinedTextField(selected.text, { value -> scenes = updateScene(scenes, selectedIndex) { it.copy(text = value.take(1_200)) } }, label = { Text("Scene text") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        val words = ScriptSceneSplitter.wordCount(selected.text)
                        Text("$words / ${ScriptSceneSplitter.MAX_WORDS_PER_SCENE} words", color = if (words > ScriptSceneSplitter.MAX_WORDS_PER_SCENE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        if (words > ScriptSceneSplitter.MAX_WORDS_PER_SCENE) Text("This scene may be too long for comfortable on-screen reading. Split or shorten it.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text("Text style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(SceneTextStyle.BIG_STATEMENT, SceneTextStyle.BULLET_POINT, SceneTextStyle.HIGHLIGHTED_KEYWORD).forEach { style ->
                                FilterChip(selected = selected.textStyle == style, onClick = { scenes = updateScene(scenes, selectedIndex) { it.copy(textStyle = style) } }, label = { Text(when(style) { SceneTextStyle.BIG_STATEMENT -> "Big"; SceneTextStyle.BULLET_POINT -> "Bullet"; SceneTextStyle.HIGHLIGHTED_KEYWORD -> "Highlight" }) })
                            }
                        }
                        Text("Background", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(SceneBackgroundType.BRAND_COLOR, SceneBackgroundType.GRADIENT).forEach { bg ->
                                    FilterChip(selected = selected.backgroundType == bg, onClick = { scenes = updateScene(scenes, selectedIndex) { it.copy(backgroundType = bg, backgroundUri = null) } }, label = { Text(if (bg == SceneBackgroundType.BRAND_COLOR) "Brand colour" else "Gradient") })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Choose image") }
                                OutlinedButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("Video clip") }
                            }
                            selected.backgroundUri?.let { Text("Background selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                            if (selected.backgroundType == SceneBackgroundType.VIDEO_CLIP) {
                                Text("Clip trim (milliseconds)", style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = selected.clipTrimStartMs.toString(),
                                        onValueChange = { value -> scenes = updateScene(scenes, selectedIndex) { it.copy(clipTrimStartMs = value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L) } },
                                        label = { Text("Start") }, modifier = Modifier.weight(1f), singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = selected.clipTrimEndMs?.toString().orEmpty(),
                                        onValueChange = { value -> scenes = updateScene(scenes, selectedIndex) { it.copy(clipTrimEndMs = value.toLongOrNull()?.takeIf { end -> end > it.clipTrimStartMs }) } },
                                        label = { Text("End (optional)") }, modifier = Modifier.weight(1f), singleLine = true
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val text = selected.text
                                val chunks = ScriptSceneSplitter.split(text)
                                val allowedChunks = chunks.take((ScriptSceneSplitter.MAX_SCENES - (scenes.size - 1)).coerceAtLeast(1))
                                if (allowedChunks.size > 1) {
                                    val replacement = allowedChunks.map { Scene(type = selected.type, text = it, textStyle = selected.textStyle, backgroundType = selected.backgroundType, backgroundUri = selected.backgroundUri) }
                                    scenes = scenes.toMutableList().also { it.removeAt(selectedIndex); it.addAll(selectedIndex, replacement) }
                                    selectedIndex = selectedIndex.coerceAtMost(scenes.lastIndex)
                                } else toastMessage = if (scenes.size >= ScriptSceneSplitter.MAX_SCENES) "This project has reached the ${ScriptSceneSplitter.MAX_SCENES}-scene limit." else "Add more text before splitting this scene."
                            }, modifier = Modifier.weight(1f)) { Text("Split") }
                            OutlinedButton(onClick = {
                                if (selectedIndex < scenes.lastIndex) {
                                    val next = scenes[selectedIndex + 1]
                                    scenes = scenes.toMutableList().also { it[selectedIndex] = selected.copy(text = listOf(selected.text, next.text).filter(String::isNotBlank).joinToString(" ")); it.removeAt(selectedIndex + 1) }
                                } else toastMessage = "There is no next scene to merge."
                            }, modifier = Modifier.weight(1f)) { Text("Merge next") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selected.transition == SceneTransition.NONE, onClick = { scenes = updateScene(scenes, selectedIndex) { it.copy(transition = SceneTransition.NONE) } }, label = { Text("No transition") })
                            FilterChip(selected = selected.transition == SceneTransition.FADE, onClick = { scenes = updateScene(scenes, selectedIndex) { it.copy(transition = SceneTransition.FADE) } }, label = { Text("Fade") })
                        }
                        if (selected.type != SceneType.CONTENT) {
                            Text("${selected.type.name.lowercase().replaceFirstChar { it.uppercase() }} is included automatically. Edit its text or delete it with the scene controls.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            toastMessage?.let { message -> Text(message, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun updateScene(scenes: List<Scene>, index: Int, transform: (Scene) -> Scene): List<Scene> =
    scenes.toMutableList().also { if (index in it.indices) it[index] = transform(it[index]) }
