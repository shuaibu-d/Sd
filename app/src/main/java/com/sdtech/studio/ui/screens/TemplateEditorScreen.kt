package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.export.ImageExportManager
import com.sdtech.studio.export.ExportSharing
import com.sdtech.studio.render.*
import com.sdtech.studio.ui.editor.EditorViewModel
import com.sdtech.studio.ui.editor.toTemplateContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(templateId: String, onExit: () -> Unit = {}, onOpenCaption: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val vm: EditorViewModel = viewModel(key = "post-editor-$templateId", factory = EditorViewModel.factory(app, templateId))
    val state by vm.state.collectAsState()
    val brandKit by app.appContainer.brandKitRepository.brandKit.collectAsStateWithLifecycle(initialValue = null)
    val logos by app.appContainer.brandKitRepository.logoAssets.collectAsStateWithLifecycle(initialValue = emptyList())
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val latestPreview by rememberUpdatedState(preview)
    var imageSlotToFill by remember { mutableStateOf<String?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }
    var exportProgressFraction by remember { mutableFloatStateOf(0f) }
    var lastExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var lastExportMime by remember { mutableStateOf("image/png") }
    var selectedRatios by remember { mutableStateOf(setOf(state.draft.ratio)) }
    var exportJpeg by remember { mutableStateOf(false) }
    var copyCaption by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slot = imageSlotToFill
        if (uri != null && slot != null) vm.importImage(uri, slot)
        imageSlotToFill = null
    }

    suspend fun runExport() {
        exporting = true
        exportProgress = "Preparing exports…"
        exportProgressFraction = 0f
        try {
            val result = ImageExportManager(context).export(templateId, state.draft,
                ImageExportManager.Request(selectedRatios.toList(), exportJpeg)) { done, total ->
                    exportProgress = "Exporting $done of $total…"
                    exportProgressFraction = done.toFloat() / total.coerceAtLeast(1)
                }
            lastExportUri = result.uris.firstOrNull()
            lastExportMime = if (exportJpeg) "image/jpeg" else "image/png"
            if (copyCaption && result.caption.isNotBlank()) {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Post caption", result.caption))
            }
            exportProgress = "Saved ${result.uris.size} file(s) to Pictures/SD Studio"
            showExportSheet = false
            Toast.makeText(context, exportProgress, Toast.LENGTH_LONG).show()
        } catch (error: Throwable) {
            exportProgress = error.message ?: "Export failed"
            Toast.makeText(context, exportProgress, Toast.LENGTH_LONG).show()
        } finally { exporting = false }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { runExport() } else Toast.makeText(context, "Storage permission is needed on Android 8 and 9.", Toast.LENGTH_LONG).show()
    }

    DisposableEffect(Unit) { onDispose { latestPreview?.recycle() } }

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.saveNow() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { vm.saveNow(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.template, state.draft, brandKit, logos) {
        val template = state.template ?: return@LaunchedEffect
        delay(320)
        val generated = withContext(Dispatchers.IO) {
            runCatching {
                TemplateRenderer(context).render(
                    template,
                    state.activeSlide.toTemplateContent(state.draft.brandLock).let { content ->
                        val counter = "%02d / %02d".format(state.draft.activeSlide + 1, state.draft.slides.size)
                        val counterLayerValues = template.layers.filterIsInstance<TemplateLayer.Text>()
                            .filter { it.text.contains("{slide_number}") }.associate { it.id to counter }
                        content.copy(values = content.values + ("slide_number" to counter) + counterLayerValues)
                    },
                    state.draft.ratio,
                    theme = template.themes.firstOrNull { it.id == if (state.draft.darkTheme) "dark" else "light" },
                    options = RenderOptions(
                        showSafeZones = state.draft.showSafeZones,
                        logoPlacementMode = if (state.draft.logoPlacement == "TIGHT") LogoPlacementMode.TIGHT else LogoPlacementMode.SAFE,
                        logoTypeOverride = state.draft.logoVariant,
                        brandColors = brandKit?.palette.orEmpty().associate { it.name.lowercase() to it.hex }
                    ),
                    brandKit = brandKit,
                    logos = logos
                )
            }.getOrNull()
        }
        val previous = preview
        preview = generated
        if (previous != null && previous !== generated) previous.recycle()
    }

    val template = state.template
    val slide = state.activeSlide
    val textLayers = template?.layers?.filterIsInstance<TemplateLayer.Text>().orEmpty()
    val imageSlots = template?.layers?.filterIsInstance<TemplateLayer.ImageSlot>().orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(template?.name ?: "Post editor", maxLines = 1); Text(if (state.isSaving) "Saving draft…" else "Draft saved automatically", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { TextButton(onClick = { vm.saveNow(); onExit() }) { Text("Done") } },
                actions = {
                    TextButton(onClick = vm::undo, enabled = true) { Text("Undo") }
                    TextButton(onClick = vm::redo, enabled = true) { Text("Redo") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (template == null) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                preview?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), contentDescription = "Live post preview", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
                } ?: Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Live preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${state.draft.activeSlide + 1} / ${state.draft.slides.size}", style = MaterialTheme.typography.labelLarge)
                }

                if (state.draft.slides.size > 1 || template.id.contains("carousel")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Carousel slides", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${state.draft.slides.size}/10", style = MaterialTheme.typography.labelMedium)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.draft.slides, key = { _, item -> item.id }) { index, item ->
                            Card(
                                modifier = Modifier.width(86.dp).pointerInput(item.id, index) {
                                    var dragDistance = 0f
                                    detectDragGesturesAfterLongPress(onDrag = { change, amount ->
                                        change.consume(); dragDistance += amount.x
                                        if (kotlin.math.abs(dragDistance) > 48f) {
                                            val target = (index + if (dragDistance > 0) 1 else -1).coerceIn(0, state.draft.slides.lastIndex)
                                            if (target != index) { vm.moveSlide(index, target); dragDistance = 0f }
                                        }
                                    })
                                },
                                colors = CardDefaults.cardColors(containerColor = if (index == state.draft.activeSlide) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${index + 1}", style = MaterialTheme.typography.titleLarge)
                                    Text(if (index == state.draft.activeSlide) "Editing" else "Slide", style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = { vm.selectSlide(index) }, contentPadding = PaddingValues(0.dp)) { Text("Open") }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::addSlide, enabled = state.draft.slides.size < 10, modifier = Modifier.weight(1f)) { Text("+ Add slide") }
                        OutlinedButton(onClick = vm::duplicateSlide, enabled = state.draft.slides.size < 10, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                        OutlinedButton(onClick = vm::deleteSlide, enabled = state.draft.slides.size > 1, modifier = Modifier.weight(1f)) { Text("Delete") }
                    }
                    Text("Tip: press and drag a slide card left or right to reorder it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedButton(onClick = vm::addSlide, enabled = state.draft.slides.size < 10, modifier = Modifier.fillMaxWidth()) { Text("Enable carousel · Add slide") }
                }

                EditorSection("Canvas") {
                    Text("Aspect ratio", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1:1", "4:5", "9:16", "16:9").forEach { ratio -> FilterChip(selected = state.draft.ratio == ratio, onClick = { vm.setRatio(ratio) }, label = { Text(ratio) }) }
                    }
                    SettingSwitch("Dark theme", state.draft.darkTheme, vm::setDark)
                    SettingSwitch("Show safe-zone guides", state.draft.showSafeZones, vm::setSafeZones)
                }

                if (textLayers.isNotEmpty()) EditorSection("Text") {
                    textLayers.forEach { layer ->
                        val current = slide.values[layer.id] ?: layer.text
                        OutlinedTextField(
                            value = current,
                            onValueChange = { vm.setText(layer.id, it) },
                            label = { Text("${layer.role.name.lowercase().replaceFirstChar(Char::uppercase)} · ${layer.id}") },
                            modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Style", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(8.dp))
                            listOf("HEADING", "BODY", "LABEL").forEach { preset ->
                                FilterChip(selected = slide.textPresets[layer.id] == preset, onClick = { vm.setTextPreset(layer.id, preset) }, label = { Text(preset.lowercase().replaceFirstChar(Char::uppercase)) })
                            }
                        }
                    }
                    SettingSwitch("Brand Lock · use brand fonts and colours", state.draft.brandLock, vm::setBrandLock)
                }

                if (imageSlots.isNotEmpty()) EditorSection("Photos") {
                    imageSlots.forEach { slot ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(slot.slot.replace('_', ' ').replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = { imageSlotToFill = slot.slot; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (slide.imagePaths.containsKey(slot.slot)) "Change photo" else "Choose photo")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = (slide.imageFits[slot.slot] ?: "CROP") == "CROP", onClick = { vm.setImageFit(slot.slot, "CROP") }, label = { Text("Crop") })
                                FilterChip(selected = slide.imageFits[slot.slot] == "FIT", onClick = { vm.setImageFit(slot.slot, "FIT") }, label = { Text("Fit") })
                            }
                            Text("Dark overlay", style = MaterialTheme.typography.labelMedium)
                            Slider(value = slide.imageOverlays[slot.slot] ?: slot.darkOverlay, onValueChange = { vm.setImageOverlay(slot.slot, it) }, valueRange = 0f..0.85f)
                        }
                    }
                }

                EditorSection("Logo") {
                    Text("Position", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.draft.logoPlacement == "SAFE", onClick = { vm.setLogoPlacement("SAFE") }, label = { Text("Safe") })
                        FilterChip(selected = state.draft.logoPlacement == "TIGHT", onClick = { vm.setLogoPlacement("TIGHT") }, label = { Text("Tight") })
                    }
                    Text("Logo variant", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("FULL", "ICON", "LIGHT", "DARK").forEach { variant -> FilterChip(selected = state.draft.logoVariant == variant, onClick = { vm.setLogoVariant(variant) }, label = { Text(variant.lowercase().replaceFirstChar(Char::uppercase)) }) }
                    }
                    Text(if (logos.any { it.type.name == state.draft.logoVariant }) "${state.draft.logoVariant.lowercase().replaceFirstChar(Char::uppercase)} logo is available in your Brand Kit." else "Add this logo variant in Brand Kit to display it in the preview.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { vm.saveNow(); onOpenCaption("template-draft-$templateId") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Edit post caption") }
                Button(onClick = { selectedRatios = setOf(state.draft.ratio); showExportSheet = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Export image") }
                lastExportUri?.let { uri -> OutlinedButton(onClick = { ExportSharing.share(context, uri, lastExportMime) }, modifier = Modifier.fillMaxWidth()) { Text("Share latest export") } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { if (!exporting) showExportSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Export your design", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Choose one or more sizes. Every carousel slide is exported as a numbered file.", style = MaterialTheme.typography.bodyMedium)
                Text("Aspect ratios", style = MaterialTheme.typography.titleSmall)
                listOf("1:1", "4:5", "9:16", "16:9").forEach { ratio ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ratio in selectedRatios, onCheckedChange = { checked -> selectedRatios = if (checked) selectedRatios + ratio else selectedRatios - ratio })
                        Text(ratio, modifier = Modifier.weight(1f))
                    }
                }
                Text("File format", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !exportJpeg, onClick = { exportJpeg = false }, label = { Text("PNG · default") })
                    FilterChip(selected = exportJpeg, onClick = { exportJpeg = true }, label = { Text("JPEG · 92%") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyCaption, onCheckedChange = { copyCaption = it })
                    Text("Copy caption to clipboard after export")
                }
                if (exporting) { LinearProgressIndicator(progress = { exportProgressFraction }, modifier = Modifier.fillMaxWidth()); Text(exportProgress) }
                Button(onClick = {
                    if (selectedRatios.isEmpty()) { Toast.makeText(context, "Select at least one ratio", Toast.LENGTH_SHORT).show() }
                    else if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else scope.launch { runExport() }
                }, enabled = !exporting && selectedRatios.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (exporting) "Exporting…" else "Export ${selectedRatios.size} size(s)") }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
