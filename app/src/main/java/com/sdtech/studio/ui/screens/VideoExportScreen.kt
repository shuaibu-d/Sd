package com.sdtech.studio.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sdtech.studio.export.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoExportScreen(projectId: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var preset by remember { mutableStateOf(VideoExportPreset.REELS) }
    var logo by remember { mutableStateOf(ExportLogoVariant.ICON) }
    var logoMode by remember { mutableStateOf(com.sdtech.studio.render.LogoPlacementMode.SAFE) }
    var use720p by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(VideoExportService.isExportInProgress()) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Choose an export preset.") }
    var error by remember { mutableStateOf<String?>(null) }
    var canRetry720 by remember { mutableStateOf(false) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var missingVoices by remember { mutableStateOf("") }
    var pendingFallback by remember { mutableStateOf(false) }
    var burnCaptions by remember { mutableStateOf(false) }
    var showProgressBar by remember { mutableStateOf(false) }
    var musicEnabled by remember { mutableStateOf(false) }
    var musicUri by remember { mutableStateOf<String?>(null) }
    var musicVolume by remember { mutableFloatStateOf(0.20f) }
    var musicLoop by remember { mutableStateOf(true) }
    var autoDuckMusic by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val app = (context.applicationContext as com.sdtech.studio.SDStudioApplication).appContainer
        val prefs = app.settingsRepository.settings.first()
        logoMode = runCatching { com.sdtech.studio.render.LogoPlacementMode.valueOf(prefs.defaultLogoMode) }.getOrDefault(com.sdtech.studio.render.LogoPlacementMode.SAFE)
        VideoExportPreset.entries.firstOrNull { it.ratio == prefs.lastUsedRatio }?.let { preset = it }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolved = runCatching {
                com.sdtech.studio.video.AndroidMediaPersistenceGateway.resolver(context)
                    .resolve(uri.toString(), "mp3")
            }.getOrDefault(com.sdtech.studio.video.domain.ResolvedMediaReference(uri.toString(), false))
            musicUri = resolved.storedUriOrPath
        }
    }

    var showThermalExportWarning by remember { mutableStateOf(false) }

    fun startExport(fallback: Boolean = use720p) {
        if (musicEnabled && musicUri.isNullOrBlank()) {
            error = "Choose a background music file or switch Background music off."
            return
        }
        error = null
        savedUri = null
        progress = 0
        use720p = fallback
        exporting = true
        status = if (fallback) "Preparing 720p export…" else "Preparing export…"
        runCatching {
            VideoExportService.start(
                context,
                VideoExportRequest(
                    projectId = projectId,
                    preset = preset,
                    logoVariant = logo,
                    use720p = fallback,
                    burnCaptions = burnCaptions,
                    showProgressBar = showProgressBar,
                    backgroundMusicUri = musicUri.takeIf { musicEnabled },
                    musicVolume = musicVolume,
                    musicLoop = musicLoop,
                    autoDuckMusic = autoDuckMusic,
                    logoPlacementMode = logoMode
                )
            )
        }.onFailure { throwable ->
            exporting = false
            val msg = throwable.message ?: "Could not start the export service."
            error = msg
            com.sdtech.studio.performance.ErrorBoundary.report(
                source = "VideoExportStart:$projectId",
                throwable = throwable,
                title = "Video export could not start",
                message = msg,
                onRetry = { startExport(fallback) }
            )
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    VideoExportService.ACTION_PROGRESS -> {
                        progress = intent.getIntExtra(VideoExportService.EXTRA_PROGRESS, 0)
                        exporting = true
                        status = "Exporting video…"
                    }
                    VideoExportService.ACTION_COMPLETE -> {
                        exporting = false
                        progress = 100
                        savedUri = intent.getStringExtra(VideoExportService.EXTRA_URI)?.let(Uri::parse)
                        missingVoices = intent.getStringExtra(VideoExportService.EXTRA_MISSING).orEmpty()
                        status = "Video saved to Movies/SD Studio."
                    }
                    VideoExportService.ACTION_ERROR -> {
                        exporting = false
                        val errText = intent.getStringExtra(VideoExportService.EXTRA_ERROR) ?: "Export failed."
                        error = errText
                        canRetry720 = intent.getBooleanExtra(VideoExportService.EXTRA_CAN_RETRY_720P, false)
                        status = "Export failed."
                        com.sdtech.studio.performance.ErrorBoundary.report(
                            source = "VideoExport:$projectId",
                            throwable = IllegalStateException(errText),
                            title = "Video export failed",
                            message = errText,
                            onRetry = {
                                if (canRetry720 && !use720p) startExport(true) else startExport(use720p)
                            }
                        )
                    }
                    VideoExportService.ACTION_CANCELLED -> {
                        exporting = false
                        status = "Export cancelled."
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(VideoExportService.ACTION_PROGRESS)
            addAction(VideoExportService.ACTION_COMPLETE)
            addAction(VideoExportService.ACTION_ERROR)
            addAction(VideoExportService.ACTION_CANCELLED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startExport(pendingFallback) }

    fun requestStartExport(fallback: Boolean = use720p) {
        pendingFallback = fallback
        if (com.sdtech.studio.performance.ThermalGuard.shouldWarnBeforeExport) {
            showThermalExportWarning = true
        } else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startExport(fallback)
        }
    }

    fun cancel() {
        context.startService(Intent(context, VideoExportService::class.java).setAction(VideoExportService.ACTION_CANCEL))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export video") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (com.sdtech.studio.performance.ThermalGuard.shouldWarnBeforeExport) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        com.sdtech.studio.performance.ThermalGuard.exportThermalWarningMessage(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Text("Export preset", style = MaterialTheme.typography.titleMedium)
            VideoExportPreset.entries.forEach { option ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        Text("${option.width} × ${option.height} · 30 fps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = preset == option, onClick = { preset = option })
                }
            }
            HorizontalDivider()
            Text("Logo overlay", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = logo == ExportLogoVariant.FULL, onClick = { logo = ExportLogoVariant.FULL }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Full") }
                SegmentedButton(selected = logo == ExportLogoVariant.ICON, onClick = { logo = ExportLogoVariant.ICON }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Icon") }
            }
            Text("Logo placement", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = logoMode == com.sdtech.studio.render.LogoPlacementMode.SAFE, onClick = { logoMode = com.sdtech.studio.render.LogoPlacementMode.SAFE }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Safe") }
                SegmentedButton(selected = logoMode == com.sdtech.studio.render.LogoPlacementMode.TIGHT, onClick = { logoMode = com.sdtech.studio.render.LogoPlacementMode.TIGHT }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Tight") }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = use720p, onCheckedChange = { use720p = it }, enabled = !exporting)
                Column {
                    Text("Use 720p fallback")
                    Text("${preset.fallbackWidth} × ${preset.fallbackHeight}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Text("Polish options", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Burn-in captions")
                    Text("Script text, brand-colour caption panel, safe-zone placement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = burnCaptions, onCheckedChange = { burnCaptions = it }, enabled = !exporting)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Teal progress bar")
                    Text("Thin timeline indicator along the bottom edge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showProgressBar, onCheckedChange = { showProgressBar = it }, enabled = !exporting)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Background music")
                    Text("Add a track, trim to video length or loop it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = musicEnabled, onCheckedChange = { musicEnabled = it }, enabled = !exporting)
            }
            if (musicEnabled) {
                OutlinedButton(onClick = { musicPicker.launch(arrayOf("audio/*")) }, enabled = !exporting, modifier = Modifier.fillMaxWidth()) {
                    Text(if (musicUri == null) "Choose audio file" else "Change music file")
                }
                musicUri?.let { Text("Selected: ${Uri.parse(it).lastPathSegment ?: "Audio file"}", style = MaterialTheme.typography.bodySmall) }
                Text("Music volume · ${(musicVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(value = musicVolume, onValueChange = { musicVolume = it }, valueRange = 0.05f..1f, enabled = !exporting)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Loop music to video length")
                    Switch(checked = musicLoop, onCheckedChange = { musicLoop = it }, enabled = !exporting)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Auto-duck under voice-over"); Text("Music is lowered to 22% of its selected volume during scenes with a voice take.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = autoDuckMusic, onCheckedChange = { autoDuckMusic = it }, enabled = !exporting)
                }
                Text("Use only music you own or have permission to use.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Not enabled in this stable export build", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Text entrance animations · static text fallback", modifier = Modifier.weight(1f))
                Switch(checked = false, onCheckedChange = {}, enabled = false)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Cross-fade scene transitions", modifier = Modifier.weight(1f))
                Switch(checked = false, onCheckedChange = {}, enabled = false)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Final-mix loudness normalisation", modifier = Modifier.weight(1f))
                Switch(checked = false, onCheckedChange = {}, enabled = false)
            }
            Text("These options remain off so the original export path stays stable. See ARCHITECTURE.md for the Media3 limitations and the safe fallback plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Output: H.264 video · AAC audio · SDR · 30 fps", style = MaterialTheme.typography.bodySmall)
            if (exporting) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text(if (progress > 0) "$progress% · $status" else "An export is running in the background. You can cancel it here.")
                OutlinedButton(onClick = ::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel export") }
            } else {
                Button(onClick = {
                    requestStartExport(use720p)
                }, enabled = !VideoExportService.isExportInProgress(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (VideoExportService.isExportInProgress()) "Another export is running" else if (use720p) "Export at 720p" else "Export video") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (canRetry720 && !use720p) {
                    Button(onClick = { requestStartExport(true) }, modifier = Modifier.fillMaxWidth()) { Text("Retry at 720p") }
                }
            }
            if (missingVoices.isNotBlank() && savedUri != null) {
                Text("Missing voice-over files: $missingVoices. Those scene positions were exported as silence.", color = MaterialTheme.colorScheme.error)
            }
            savedUri?.let { uri ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share video"))
                    }, modifier = Modifier.weight(1f)) { Text("Share") }
                    OutlinedButton(onClick = {
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(view) }.onFailure {
                            error = "No video player is available to open this export."
                        }
                    }, modifier = Modifier.weight(1f)) { Text("View") }
                }
            }
        }
    }

    if (showThermalExportWarning) {
        AlertDialog(
            onDismissRequest = { showThermalExportWarning = false },
            title = { Text("Phone is warm") },
            text = { Text(com.sdtech.studio.performance.ThermalGuard.exportThermalWarningMessage()) },
            confirmButton = {
                Button(onClick = {
                    showThermalExportWarning = false
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startExport(pendingFallback)
                    }
                }) { Text("Export anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showThermalExportWarning = false }) { Text("Wait to cool down") }
            }
        )
    }
}
