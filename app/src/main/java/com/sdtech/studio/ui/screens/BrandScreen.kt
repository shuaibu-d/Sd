package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.brand.BrandBackupManager
import com.sdtech.studio.brand.LogoImageProcessor
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.CaptionTemplate
import com.sdtech.studio.data.local.entity.HashtagGroup
import com.sdtech.studio.data.local.entity.LogoAsset
import com.sdtech.studio.data.model.LogoType
import com.sdtech.studio.data.model.PaletteColor
import com.sdtech.studio.data.model.StudioDivision
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val officialPalette = listOf(
    PaletteColor("Midnight Navy", "#101C2C"), PaletteColor("Innovation Teal", "#16A6A0"),
    PaletteColor("Premium Gold", "#D9B45B"), PaletteColor("Cloud White", "#F3F5F8"),
    PaletteColor("Slate Grey", "#263445")
)

@Composable
fun BrandScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val container = app.appContainer
    val scope = rememberCoroutineScope()
    val brand by container.brandKitRepository.brandKit.collectAsStateWithLifecycle(initialValue = null)
    val logos by container.brandKitRepository.logoAssets.collectAsStateWithLifecycle(initialValue = emptyList())
    val captions by container.contentLibraryRepository.captionTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val hashtags by container.contentLibraryRepository.hashtagGroups.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSlot by remember { mutableStateOf(LogoType.FULL) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tolerance by remember { mutableFloatStateOf(18f) }
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }
    var editorOpen by remember { mutableStateOf(false) }
    var paletteIndex by remember { mutableIntStateOf(-1) }
    var paletteHex by remember { mutableStateOf("") }
    var contactDialog by remember { mutableStateOf(false) }
    var editCaption by remember { mutableStateOf<CaptionTemplate?>(null) }
    var editHashtag by remember { mutableStateOf<HashtagGroup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val palette = brand?.palette?.takeIf { it.isNotEmpty() } ?: officialPalette

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { LogoImageProcessor.decode(context.contentResolver, uri) } }
                .onSuccess { sourceBitmap = it; previewBitmap = it; cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f; editorOpen = true }
                .onFailure { message = it.message ?: "Could not open image." }
            busy = false
        }
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            runCatching { BrandBackupManager.export(context, container.database, uri) }
                .onSuccess { message = "Brand Kit backup exported successfully." }
                .onFailure { message = "Export failed: ${it.message ?: it.javaClass.simpleName}" }
            busy = false
        }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            runCatching { BrandBackupManager.import(context, container.database, uri) }
                .onSuccess { message = "Brand Kit imported successfully." }
                .onFailure { message = "Import failed: ${it.message ?: it.javaClass.simpleName}" }
            busy = false
        }
    }

    ScreenScaffold {
        SectionHeader(title = "Brand Kit", subtitle = "Keep every SD Technologies asset consistent.")
        SectionHeader(title = "Logo preparation", subtitle = "Prepare transparent PNGs for each brand use.")
        Text("Select a logo slot", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogoType.entries.forEach { type ->
                FilterChip(selected = selectedSlot == type, onClick = { selectedSlot = type }, label = { Text(logoSlotLabel(type)) })
            }
        }
        LogoType.entries.forEach { type ->
            val slotLogo = logos.firstOrNull { it.type == type }
            Text(logoSlotLabel(type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LogoTile(slotLogo?.filePath, Color(0xFF101C2C), "Navy", Modifier.weight(1f))
                LogoTile(slotLogo?.filePath, Color.White, "White", Modifier.weight(1f))
            }
        }
        Button(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text("Import / prepare ${selectedSlot.name} logo") }
        Text("Use PNG or a clear image. Images are processed at a maximum 2048 px edge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionHeader(title = "Official colours", subtitle = if (brand?.brandLock == true) "Brand lock is on: use palette colours only." else "Tap a colour to edit its hex value.")
        palette.forEachIndexed { index, color ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(parseColor(color.hex)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp)))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(color.name, fontWeight = FontWeight.SemiBold)
                    Text(color.hex, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(enabled = brand?.brandLock == false, onClick = { paletteIndex = index; paletteHex = color.hex }) { Text(if (brand?.brandLock == true) "Locked" else "Edit") }
            }
        }
        TextButton(onClick = { scope.launch { saveBrand(brand ?: defaultBrand(), palette = officialPalette, repository = container.brandKitRepository) } }) { Text("Reset palette to official") }

        SectionHeader(title = "Brand details", subtitle = "Contact details and the default call-to-action.")
        OutlinedButton(onClick = { contactDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit contact line and CTA") }
        Text("Website: ${brand?.website.orEmpty().ifBlank { "Not set" }}", style = MaterialTheme.typography.bodyMedium)
        Text("WhatsApp: ${brand?.whatsapp.orEmpty().ifBlank { "Not set" }}", style = MaterialTheme.typography.bodyMedium)
        Text("Social: ${brand?.handle.orEmpty().ifBlank { "Not set" }}", style = MaterialTheme.typography.bodyMedium)
        Text("Default CTA: ${brand?.defaultCta.orEmpty().ifBlank { "Not set" }}", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Brand lock", fontWeight = FontWeight.SemiBold)
                Text("Restrict colour selection to the official palette.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = brand?.brandLock ?: true, onCheckedChange = { checked ->
                scope.launch { saveBrand(brand ?: defaultBrand(), repository = container.brandKitRepository, brandLock = checked) }
            })
        }

        SectionHeader(title = "Caption templates", subtitle = "Reusable copy for each SD division.")
        captions.forEach { item ->
            LibraryItem(item.title, item.division.name, item.body, onEdit = { editCaption = item }, onDelete = { scope.launch { container.contentLibraryRepository.deleteCaption(item.id) } })
        }
        OutlinedButton(onClick = { editCaption = CaptionTemplate(title = "", body = "", division = StudioDivision.GENERAL) }, modifier = Modifier.fillMaxWidth()) { Text("Add caption template") }

        SectionHeader(title = "Hashtag groups", subtitle = "Organise tags by business division.")
        hashtags.forEach { item ->
            LibraryItem(item.name, item.division.name, item.tags.joinToString(" "), onEdit = { editHashtag = item }, onDelete = { scope.launch { container.contentLibraryRepository.deleteHashtag(item.id) } })
        }
        OutlinedButton(onClick = { editHashtag = HashtagGroup(name = "", tags = emptyList(), division = StudioDivision.GENERAL) }, modifier = Modifier.fillMaxWidth()) { Text("Add hashtag group") }

        SectionHeader(title = "Backup", subtitle = "Export or restore your brand settings and logo PNGs.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { backupExporter.launch("SD-Studio-Brand-Kit.zip") }, modifier = Modifier.weight(1f)) { Text("Export ZIP") }
            OutlinedButton(onClick = { backupImporter.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Import ZIP") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    if (editorOpen && sourceBitmap != null) {
        LogoPrepDialog(
            bitmap = previewBitmap ?: sourceBitmap!!,
            tolerance = tolerance,
            onTolerance = { tolerance = it },
            cropLeft = cropLeft, cropTop = cropTop, cropRight = cropRight, cropBottom = cropBottom,
            onCrop = { l, t, r, b -> cropLeft = l; cropTop = t; cropRight = r; cropBottom = b },
            allowMonogramCrop = selectedSlot == LogoType.ICON,
            onRemoveBackground = {
                scope.launch { busy = true; previewBitmap = withContext(Dispatchers.Default) { LogoImageProcessor.removeEdgeBackground(sourceBitmap!!, tolerance.toInt()) }; busy = false }
            },
            onAutoCrop = { scope.launch { previewBitmap = withContext(Dispatchers.Default) { LogoImageProcessor.cropToVisible(previewBitmap ?: sourceBitmap!!) } } },
            onSave = {
                scope.launch {
                    busy = true
                    runCatching {
                        val base = previewBitmap ?: sourceBitmap!!
                        val cropped = withContext(Dispatchers.Default) { LogoImageProcessor.crop(base, cropLeft, cropTop, cropRight, cropBottom) }
                        val padded = withContext(Dispatchers.Default) { LogoImageProcessor.cropToVisible(cropped, 0.025f) }
                        val file = LogoImageProcessor.savePng(context.filesDir, padded, selectedSlot.name.lowercase())
                        container.brandKitRepository.replaceLogoAsset(LogoAsset(type = selectedSlot, filePath = file.absolutePath, width = padded.width, height = padded.height))
                        editorOpen = false
                    }.onFailure { message = it.message ?: "Unable to save logo." }
                    busy = false
                }
            }, onClose = { editorOpen = false }
        )
    }

    if (paletteIndex >= 0) {
        AlertDialog(onDismissRequest = { paletteIndex = -1 }, title = { Text("Edit colour") },
            text = { StudioTextEntry(paletteHex, { paletteHex = it }, "Hex colour") },
            confirmButton = { TextButton(onClick = {
                if (paletteHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                    val updated = palette.toMutableList(); updated[paletteIndex] = updated[paletteIndex].copy(hex = paletteHex.uppercase())
                    scope.launch { saveBrand(brand ?: defaultBrand(), repository = container.brandKitRepository, palette = updated) }
                    paletteIndex = -1
                } else message = "Enter a valid hex value such as #16A6A0."
            }) { Text("Save") } }, dismissButton = { TextButton(onClick = { paletteIndex = -1 }) { Text("Cancel") } })
    }

    if (contactDialog) ContactDialog(brand ?: defaultBrand(), onDismiss = { contactDialog = false }, onSave = { updated ->
        scope.launch { container.brandKitRepository.saveBrandKit(updated); contactDialog = false }
    })
    editCaption?.let { item -> CaptionDialog(item, onDismiss = { editCaption = null }, onSave = { updated -> scope.launch { container.contentLibraryRepository.saveCaption(updated); editCaption = null } }) }
    editHashtag?.let { item -> HashtagDialog(item, onDismiss = { editHashtag = null }, onSave = { updated -> scope.launch { container.contentLibraryRepository.saveHashtag(updated); editHashtag = null } }) }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(text) }) }
}

@Composable
private fun LogoTile(path: String?, tile: Color, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(14.dp)).background(tile), contentAlignment = Alignment.Center) {
            if (path != null && File(path).exists()) {
                var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(path) {
                    bitmap = com.sdtech.studio.performance.RenderDispatcher.withRenderContext {
                        com.sdtech.studio.performance.CentralBitmapPool.decodeFileCached(path, maxEdgePx = 512)
                    }
                }
                val shown = bitmap
                if (shown != null && !shown.isRecycled) {
                    androidx.compose.foundation.Image(shown.asImageBitmap(), contentDescription = "$label logo preview", modifier = Modifier.fillMaxSize().padding(16.dp))
                }
            } else Text("No logo", color = if (tile == Color.White) Color.DarkGray else Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    }
}

@Composable
private fun LogoPrepDialog(bitmap: Bitmap, tolerance: Float, onTolerance: (Float) -> Unit, cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float, onCrop: (Float, Float, Float, Float) -> Unit, allowMonogramCrop: Boolean, onRemoveBackground: () -> Unit, onAutoCrop: () -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Prepare logo") }, text = {
        Column(Modifier.heightIn(max = 570.dp).verticalScroll(rememberScrollState())) {
            CheckerboardPreview(bitmap)
            Text("Background tolerance · ${tolerance.toInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(value = tolerance, onValueChange = onTolerance, valueRange = 1f..80f)
            OutlinedButton(onClick = onRemoveBackground, modifier = Modifier.fillMaxWidth()) { Text("Make transparent · remove edge background") }
            OutlinedButton(onClick = onAutoCrop, modifier = Modifier.fillMaxWidth()) { Text("Auto-crop visible pixels") }
            if (allowMonogramCrop) {
                Text("Crop monogram · adjust the four edges", style = MaterialTheme.typography.labelLarge)
                Text("Left"); Slider(cropLeft, { onCrop(it.coerceAtMost(cropRight - .05f), cropTop, cropRight, cropBottom) }, valueRange = 0f..0.9f)
                Text("Top"); Slider(cropTop, { onCrop(cropLeft, it.coerceAtMost(cropBottom - .05f), cropRight, cropBottom) }, valueRange = 0f..0.9f)
                Text("Right"); Slider(cropRight, { onCrop(cropLeft, cropTop, it.coerceAtLeast(cropLeft + .05f), cropBottom) }, valueRange = .1f..1f)
                Text("Bottom"); Slider(cropBottom, { onCrop(cropLeft, cropTop, cropRight, it.coerceAtLeast(cropTop + .05f)) }, valueRange = .1f..1f)
            }
        }
    }, confirmButton = { TextButton(onClick = onSave) { Text("Save PNG") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun CheckerboardPreview(bitmap: Bitmap) {
    val checkerPath = remember { androidx.compose.ui.graphics.Path() }
    val checkerColor = Color(0xFFBFC5CE)
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE7E9ED)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            checkerPath.reset()
            val cell = 16.dp.toPx()
            val rows = (size.height / cell).toInt()
            val cols = (size.width / cell).toInt()
            for (y in 0..rows) {
                for (x in 0..cols) {
                    if ((x + y) % 2 == 0) {
                        val left = x * cell
                        val top = y * cell
                        checkerPath.addRect(androidx.compose.ui.geometry.Rect(left, top, left + cell, top + cell))
                    }
                }
            }
            drawPath(checkerPath, checkerColor)
        }
        androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = "Logo preview on a transparency checkerboard", modifier = Modifier.fillMaxSize().padding(10.dp))
    }
}

@Composable
private fun ContactDialog(brand: BrandKit, onDismiss: () -> Unit, onSave: (BrandKit) -> Unit) {
    var website by remember { mutableStateOf(brand.website) }; var whatsapp by remember { mutableStateOf(brand.whatsapp) }
    var handle by remember { mutableStateOf(brand.handle) }; var cta by remember { mutableStateOf(brand.defaultCta) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Brand contact details") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            StudioTextEntry(website, { website = it }, "Website")
            StudioTextEntry(whatsapp, { whatsapp = it }, "WhatsApp number")
            StudioTextEntry(handle, { handle = it }, "Social handle")
            StudioTextEntry(cta, { cta = it }, "Default call-to-action")
        }
    }, confirmButton = { TextButton(onClick = { onSave(brand.copy(website = website, whatsapp = whatsapp, handle = handle, defaultCta = cta)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CaptionDialog(item: CaptionTemplate, onDismiss: () -> Unit, onSave: (CaptionTemplate) -> Unit) {
    var title by remember(item.id) { mutableStateOf(item.title) }; var body by remember(item.id) { mutableStateOf(item.body) }; var division by remember(item.id) { mutableStateOf(item.division) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item.id == 0L) "Add caption" else "Edit caption") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            StudioTextEntry(title, { title = it }, "Title")
            StudioTextEntry(body, { body = it }, "Caption", minLines = 3)
            DivisionPicker(division) { division = it }
        }
    }, confirmButton = { TextButton(enabled = title.isNotBlank() && body.isNotBlank(), onClick = { onSave(item.copy(title = title.trim(), body = body.trim(), division = division)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun HashtagDialog(item: HashtagGroup, onDismiss: () -> Unit, onSave: (HashtagGroup) -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }; var tags by remember(item.id) { mutableStateOf(item.tags.joinToString(" ")) }; var division by remember(item.id) { mutableStateOf(item.division) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item.id == 0L) "Add hashtag group" else "Edit hashtag group") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            StudioTextEntry(name, { name = it }, "Group name")
            StudioTextEntry(tags, { tags = it }, "Hashtags, separated by spaces", minLines = 3)
            DivisionPicker(division) { division = it }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(item.copy(name = name.trim(), tags = tags.split(Regex("\\s+")).filter { it.isNotBlank() }.map { if (it.startsWith("#")) it else "#$it" }, division = division)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun DivisionPicker(value: StudioDivision, onChange: (StudioDivision) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Division: ${value.name}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StudioDivision.entries.forEach { division -> DropdownMenuItem(text = { Text(division.name) }, onClick = { onChange(division); expanded = false }) }
        }
    }
}

@Composable
private fun LibraryItem(title: String, category: String, body: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(category, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp) }
                TextButton(onClick = onEdit) { Text("Edit") }; TextButton(onClick = onDelete) { Text("Delete") }
            }
            Text(body, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
    }
}

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
private fun defaultBrand() = BrandKit(name = "SD Technologies", slogan = "Create Without Limits.", palette = officialPalette, website = "", whatsapp = "", handle = "", defaultCta = "Create Without Limits.", brandLock = true)
private suspend fun saveBrand(current: BrandKit, repository: com.sdtech.studio.data.repository.BrandKitRepository, palette: List<PaletteColor>? = null, brandLock: Boolean? = null) {
    repository.saveBrandKit(current.copy(palette = palette ?: current.palette.ifEmpty { officialPalette }, brandLock = brandLock ?: current.brandLock))
}


private fun logoSlotLabel(type: LogoType): String = when (type) {
    LogoType.FULL -> "Full logo (default)"
    LogoType.ICON -> "Icon / monogram"
    LogoType.LIGHT -> "For light backgrounds"
    LogoType.DARK -> "For dark backgrounds"
}


@Composable
private fun StudioTextEntry(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            minLines = minLines,
            maxLines = if (minLines > 1) 6 else 1
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
