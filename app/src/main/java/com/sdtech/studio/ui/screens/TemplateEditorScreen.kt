package com.sdtech.studio.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.export.ExportSharing
import com.sdtech.studio.export.ImageExportManager
import com.sdtech.studio.render.ImageFit
import com.sdtech.studio.render.LogoPlacementMode
import com.sdtech.studio.render.RenderOptions
import com.sdtech.studio.render.TemplateLayer
import com.sdtech.studio.render.TemplateRenderer
import com.sdtech.studio.ui.components.BrandScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.editor.EditorViewModel
import com.sdtech.studio.ui.editor.toTemplateContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var imageSlotToFill by remember { mutableStateOf<String?>(null) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }
    var exportProgressFraction by remember { mutableFloatStateOf(0f) }
    var lastExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var lastExportMime by remember { mutableStateOf("image/png") }
    var selectedRatios by remember { mutableStateOf(setOf("1:1")) }
    var exportJpeg by remember { mutableStateOf(false) }
    var copyCaption by remember { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var styleOpenFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slot = imageSlotToFill
        if (uri != null && slot != null) vm.importImage(uri, slot)
    }

    suspend fun runExport() {
        exporting = true
        exportProgress = "Preparing exports\u2026"
        exportProgressFraction = 0f
        try {
            val result = ImageExportManager(context).export(
                templateId, state.draft, ImageExportManager.Request(selectedRatios.toList(), exportJpeg)
            ) { done, total ->
                exportProgress = "Exporting $done of $total\u2026"
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
        } finally {
            exporting = false
        }
    }

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { runExport() }
        else Toast.makeText(context, "Storage permission is needed on Android 8 and 9.", Toast.LENGTH_LONG).show()
    }

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.saveNow() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { vm.saveNow(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Live preview. Old bitmaps are never recycled manually (recycling a bitmap that is still on screen crashes the app).
    LaunchedEffect(state.template, state.draft, brandKit, logos) {
        val template = state.template ?: return@LaunchedEffect
        val current = state
        delay(250)
        val generated = withContext(Dispatchers.IO) {
            runCatching {
                val counter = "%02d / %02d".format(current.draft.activeSlide + 1, current.draft.slides.size)
                val counterLayerValues = template.layers.filterIsInstance<TemplateLayer.Text>()
                    .filter { it.text.contains("{slide_number}") }.associate { it.id to counter }
                val content = current.activeSlide.toTemplateContent(current.draft.brandLock).let {
                    it.copy(values = it.values + ("slide_number" to counter) + counterLayerValues)
                }
                TemplateRenderer(context).render(
                    template, content, current.draft.ratio,
                    theme = template.themes.firstOrNull { it.id == if (current.draft.darkTheme) "dark" else "light" } ?: template.themes.firstOrNull(),
                    options = RenderOptions(
                        showSafeZones = current.draft.showSafeZones,
                        logoPlacementMode = if (current.draft.logoPlacement == "TIGHT") LogoPlacementMode.TIGHT else LogoPlacementMode.SAFE,
                        logoTypeOverride = current.draft.logoVariant
                    ),
                    brandKit = brandKit, logos = logos
                )
            }.getOrNull()
        }
        if (generated != null) preview = generated
    }

    val template = state.template
    val slide = state.activeSlide
    val textLayers = template?.layers?.filterIsInstance<TemplateLayer.Text>()?.filter { it.editable }.orEmpty()
    val imageSlots = template?.layers?.filterIsInstance<TemplateLayer.ImageSlot>().orEmpty()
    val tabNames = buildList { add("Text"); if (imageSlots.isNotEmpty()) add("Photos"); add("Design"); add("Slides") }
    val activeTab = tabNames.getOrElse(tabIndex) { tabNames.first() }

    BrandScaffold(
        title = template?.name ?: "Post editor",
        subtitle = if (state.isSaving) "Saving draft\u2026" else "Draft saved automatically",
        onBack = { vm.saveNow(); onExit() },
        actions = {
            IconButton(onClick = { vm.undo() }) { SDIcon(SDIconKind.Undo, tint = MaterialTheme.colorScheme.onBackground) }
            IconButton(onClick = { vm.redo() }) { SDIcon(SDIconKind.Redo, tint = MaterialTheme.colorScheme.onBackground) }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(290.dp).padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                val shown = preview
                if (shown != null && !shown.isRecycled) {
                    Image(shown.asImageBitmap(), contentDescription = "Live preview", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp))
                } else CircularProgressIndicator()
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabNames.forEachIndexed { index, name ->
                    FilterChip(selected = name == activeTab, onClick = { tabIndex = index }, label = { Text(name) })
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (template == null) {
                    Text(state.error ?: "Loading template\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (activeTab) {
                    "Text" -> {
                        textLayers.forEach { layer ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = slide.values[layer.id].orEmpty(),
                                    onValueChange = { vm.setText(layer.id, it) },
                                    label = { Text(layer.id.replace('_', ' ').replaceFirstChar(Char::uppercase)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = if (layer.maxLines > 1) 2 else 1,
                                    maxLines = 6,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                TextButton(onClick = { styleOpenFor = if (styleOpenFor == layer.id) null else layer.id }) {
                                    Text(if (styleOpenFor == layer.id) "Hide text style" else "Text style")
                                }
                                if (styleOpenFor == layer.id) {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("" to "Original", "HEADING" to "Heading", "BODY" to "Body", "LABEL" to "Label").forEach { (value, label) ->
                                            FilterChip(
                                                selected = (slide.textPresets[layer.id] ?: "") == value,
                                                onClick = { vm.setTextPreset(layer.id, value) },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Photos" -> {
                        imageSlots.forEach { photo ->
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Photo", style = MaterialTheme.typography.titleMedium)
                                    Button(onClick = { imageSlotToFill = photo.slot; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (slide.imagePaths.containsKey(photo.slot)) "Replace photo" else "Choose photo")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(selected = (slide.imageFits[photo.slot] ?: photo.fit.name) == "CROP",
                                            onClick = { vm.setImageFit(photo.slot, "CROP") }, label = { Text("Fill frame") })
                                        FilterChip(selected = (slide.imageFits[photo.slot] ?: photo.fit.name) == "FIT",
                                            onClick = { vm.setImageFit(photo.slot, "FIT") }, label = { Text("Show whole photo") })
                                    }
                                    Text("Dark overlay", style = MaterialTheme.typography.labelMedium)
                                    Slider(value = slide.imageOverlays[photo.slot] ?: photo.darkOverlay,
                                        onValueChange = { vm.setImageOverlay(photo.slot, it) }, valueRange = 0f..0.85f)
                                }
                            }
                        }
                    }
                    "Design" -> {
                        EditorSection("Canvas") {
                            Text("Size", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("1:1", "4:5", "9:16", "16:9").forEach { ratio ->
                                    FilterChip(selected = state.draft.ratio == ratio, onClick = { vm.setRatio(ratio) }, label = { Text(ratio) })
                                }
                            }
                            Text("Theme", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = state.draft.darkTheme, onClick = { vm.setDark(true) }, label = { Text("Dark") })
                                FilterChip(selected = !state.draft.darkTheme, onClick = { vm.setDark(false) }, label = { Text("Light") })
                            }
                            SettingSwitch("Show safe-zone guides", state.draft.showSafeZones) { vm.setSafeZones(it) }
                        }
                        EditorSection("Logo") {
                            Text("Position", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = state.draft.logoPlacement == "SAFE", onClick = { vm.setLogoPlacement("SAFE") }, label = { Text("Safe") })
                                FilterChip(selected = state.draft.logoPlacement == "TIGHT", onClick = { vm.setLogoPlacement("TIGHT") }, label = { Text("Tight corner") })
                            }
                            Text("Version", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ICON" to "Icon", "FULL" to "Full logo", "LIGHT" to "For light backgrounds", "DARK" to "For dark backgrounds").forEach { (value, label) ->
                                    FilterChip(selected = state.draft.logoVariant == value, onClick = { vm.setLogoVariant(value) }, label = { Text(label) })
                                }
                            }
                            Text(
                                if (logos.any { it.type.name == state.draft.logoVariant }) "Your logo is placed in the top-right corner automatically."
                                else "Add this logo version in Brand Kit to see it here.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SettingSwitch("Brand lock (keep text styles on brand)", state.draft.brandLock) { vm.setBrandLock(it) }
                    }
                    else -> {
                        EditorSection("Carousel slides") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(state.draft.slides) { index, _ ->
                                    FilterChip(selected = index == state.draft.activeSlide, onClick = { vm.selectSlide(index) },
                                        label = { Text("Slide ${index + 1}") })
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.addSlide() }) { Text("Add") }
                                OutlinedButton(onClick = { vm.duplicateSlide() }) { Text("Duplicate") }
                                OutlinedButton(onClick = { vm.deleteSlide() }, enabled = state.draft.slides.size > 1) { Text("Delete") }
                                OutlinedButton(onClick = { vm.moveSlide(state.draft.activeSlide, state.draft.activeSlide - 1) },
                                    enabled = state.draft.activeSlide > 0) { Text("Move left") }
                                OutlinedButton(onClick = { vm.moveSlide(state.draft.activeSlide, state.draft.activeSlide + 1) },
                                    enabled = state.draft.activeSlide < state.draft.slides.lastIndex) { Text("Move right") }
                            }
                            Text("Up to 10 slides. Each slide is exported as its own numbered file.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.saveNow(); onOpenCaption("template-draft-$templateId") },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)
                ) { Text("Caption") }
                Button(
                    onClick = { selectedRatios = setOf(state.draft.ratio); showExportSheet = true },
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp)
                ) { SDIcon(SDIconKind.Download, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Export") }
            }
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { if (!exporting) showExportSheet = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Export your design", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Choose one or more sizes. Every carousel slide is exported as a numbered file.", style = MaterialTheme.typography.bodyMedium)
                listOf("1:1" to "Square", "4:5" to "Feed portrait", "9:16" to "Reels, Stories, Status", "16:9" to "Landscape").forEach { (ratio, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ratio in selectedRatios,
                            onCheckedChange = { checked -> selectedRatios = if (checked) selectedRatios + ratio else selectedRatios - ratio })
                        Text("$ratio  \u2022  $label", modifier = Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !exportJpeg, onClick = { exportJpeg = false }, label = { Text("PNG \u2022 best quality") })
                    FilterChip(selected = exportJpeg, onClick = { exportJpeg = true }, label = { Text("JPEG \u2022 smaller") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = copyCaption, onCheckedChange = { copyCaption = it })
                    Text("Copy caption to clipboard after export")
                }
                if (exporting) {
                    LinearProgressIndicator(progress = { exportProgressFraction }, modifier = Modifier.fillMaxWidth())
                    Text(exportProgress)
                }
                Button(
                    onClick = {
                        if (selectedRatios.isEmpty()) Toast.makeText(context, "Select at least one size", Toast.LENGTH_SHORT).show()
                        else if (Build.VERSION.SDK_INT < 29 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        ) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else scope.launch { runExport() }
                    },
                    enabled = !exporting && selectedRatios.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                ) { Text(if (exporting) "Exporting\u2026" else "Export ${selectedRatios.size} size(s)") }
                lastExportUri?.let { uri ->
                    OutlinedButton(onClick = { ExportSharing.share(context, uri, lastExportMime) }, modifier = Modifier.fillMaxWidth()) {
                        SDIcon(SDIconKind.Share, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("Share latest export")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
