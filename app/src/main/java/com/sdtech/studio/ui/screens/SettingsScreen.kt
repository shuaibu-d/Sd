package com.sdtech.studio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.sdtech.studio.CrashLogger
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.model.AppSettings
import com.sdtech.studio.export.VideoExportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val repo = app.appContainer.settingsRepository
    val settings by repo.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var appStorageBytes by remember { mutableLongStateOf(0L) }
    var crashLog by remember { mutableStateOf<String?>(null) }
    var showCrash by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                "${it.versionName} (${PackageInfoCompat.getLongVersionCode(it)})"
            }
        }.getOrDefault("Unknown")
    }
    fun sizeOf(file: File): Long = runCatching {
        if (!file.exists()) 0L else if (file.isFile) file.length() else file.listFiles()?.sumOf(::sizeOf) ?: 0L
    }.getOrDefault(0L)
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L) }
        appStorageBytes = withContext(Dispatchers.IO) {
            sizeOf(context.filesDir) + sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L) +
                (context.getDatabasePath("sd_studio.db").parentFile?.let(::sizeOf) ?: 0L) +
                (context.getExternalFilesDir(null)?.let(::sizeOf) ?: 0L)
        }
        crashLog = withContext(Dispatchers.IO) { CrashLogger.readLatest(app) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { com.sdtech.studio.ui.components.SDIcon(com.sdtech.studio.ui.components.SDIconKind.Back) } }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Text("Theme")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = settings.themeMode == option.first,
                        onClick = { scope.launch { repo.setThemeMode(option.first) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(option.second) }
                }
            }
            HorizontalDivider()
            Text("Defaults", style = MaterialTheme.typography.titleMedium)
            Text("Default canvas ratio")
            listOf("9:16", "4:5", "1:1", "16:9").forEach { ratio ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ratio, modifier = Modifier.alignByBaseline())
                    RadioButton(selected = settings.lastUsedRatio == ratio, onClick = { scope.launch { repo.setLastUsedRatio(ratio) } })
                }
            }
            Text("Default logo placement")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(settings.defaultLogoMode == "SAFE", { scope.launch { repo.setDefaultLogoMode("SAFE") } }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Safe") }
                SegmentedButton(settings.defaultLogoMode == "TIGHT", { scope.launch { repo.setDefaultLogoMode("TIGHT") } }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Tight") }
            }
            HorizontalDivider()
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Text("App data on this device: ${formatBytes(appStorageBytes)}", style = MaterialTheme.typography.bodyLarge)
            Text("Temporary cache: ${formatBytes(cacheBytes)}", style = MaterialTheme.typography.bodyMedium)
            Text("Shared Movies/SD Studio exports are not included in this app-private storage estimate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Clear Cache removes internal and external temporary previews/export work files. Shared Movies/SD Studio exports are not included in the storage estimate. Saved projects and recordings are kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { showClearConfirm = true },
                enabled = !busy && !VideoExportService.isExportInProgress(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (VideoExportService.isExportInProgress()) "Export in progress — cache locked" else "Clear Cache") }
            clearMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            HorizontalDivider()
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { crashLog = CrashLogger.readLatest(app); showCrash = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("View Crash Log")
            }
            HorizontalDivider()
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("SD Studio", style = MaterialTheme.typography.titleLarge)
            Text("Version $version")
            Text("Create branded social posts and short videos. Your projects and media are stored locally on this device.", style = MaterialTheme.typography.bodyMedium)
            Text("Privacy: SD Studio does not require an account or send project content to an SD Studio server. Imported media, drafts, and recordings remain on this device unless you choose to share or move them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showClearConfirm) AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("Clear temporary cache?") },
        text = { Text("Temporary files and cached previews will be deleted. Saved projects, voice recordings, and exported videos will remain.") },
        confirmButton = {
            TextButton(onClick = {
                showClearConfirm = false
                if (VideoExportService.isExportInProgress()) { clearMessage = "Cache cannot be cleared during an export."; return@TextButton }
                busy = true
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        val internal = context.cacheDir.listFiles()?.sumOf { file -> sizeOf(file).also { runCatching { file.deleteRecursively() } } } ?: 0L
                        val external = context.externalCacheDir?.let { dir ->
                            dir.listFiles()?.sumOf { file -> sizeOf(file).also { runCatching { file.deleteRecursively() } } } ?: 0L
                        } ?: 0L
                        internal + external
                    }
                    cacheBytes = withContext(Dispatchers.IO) { sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L) }
                    appStorageBytes = withContext(Dispatchers.IO) {
                        sizeOf(context.filesDir) + sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L) +
                            (context.getDatabasePath("sd_studio.db").parentFile?.let(::sizeOf) ?: 0L) +
                            (context.getExternalFilesDir(null)?.let(::sizeOf) ?: 0L)
                    }
                    busy = false
                    clearMessage = "Cleared ${formatBytes(deleted)} of temporary files."
                }
            }) { Text("Clear") }
        },
        dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
    )
    if (showCrash) AlertDialog(
        onDismissRequest = { showCrash = false },
        title = { Text("Crash Log") },
        text = { Text(crashLog ?: "No crash log is available.", modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = { CrashLogger.clear(app); crashLog = null; showCrash = false }) { Text("Clear log") } },
        dismissButton = { TextButton(onClick = { showCrash = false }) { Text("Close") } }
    )
}
