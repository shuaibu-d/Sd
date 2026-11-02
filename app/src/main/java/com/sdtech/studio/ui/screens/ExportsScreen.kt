package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.sdtech.studio.data.local.entity.ExportRecord
import com.sdtech.studio.export.ExportSharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportsScreen(onEditAgain: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ExportRecord>>(emptyList()) }
    var selected by remember { mutableStateOf<ExportRecord?>(null) }
    LaunchedEffect(Unit) { app.appContainer.exportRepository.exports.collectLatest { records = it } }

    Scaffold(topBar = { TopAppBar(title = { Text("Exports") }) }) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No exports yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your saved PNG and JPEG designs will appear here.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.id }) { record ->
                    ExportCard(record = record, onClick = { selected = record })
                }
            }
        }
    }

    selected?.let { record ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${record.ratio} · ${record.mimeType.substringAfter('/').uppercase()}", style = MaterialTheme.typography.titleLarge)
                Button(onClick = {
                    runCatching { ExportSharing.open(context, Uri.parse(record.filePath), record.mimeType) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Open") }
                OutlinedButton(onClick = { ExportSharing.share(context, Uri.parse(record.filePath), record.mimeType) }, modifier = Modifier.fillMaxWidth()) { Text("Share") }
                OutlinedButton(onClick = {
                    val templateId = record.projectId.removePrefix("template-draft-")
                    selected = null
                    onEditAgain(templateId)
                }, modifier = Modifier.fillMaxWidth()) { Text("Edit again") }
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            val uri = Uri.parse(record.filePath)
                            if (uri.scheme == "file") {
                                val path = uri.path.orEmpty()
                                context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    "${MediaStore.Images.Media.DATA}=?", arrayOf(path))
                                File(path).delete()
                            } else context.contentResolver.delete(uri, null, null)
                            app.appContainer.exportRepository.deleteExport(record.id)
                        }
                        withContext(Dispatchers.Main) { selected = null }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Delete export", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ExportCard(record: ExportRecord, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(record.filePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(record.filePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(record.filePath)
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path, options)
                else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Exported design", modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(10.dp))) }
                ?: Box(Modifier.fillMaxWidth().aspectRatio(0.8f), contentAlignment = Alignment.Center) { Text("Preview unavailable") }
            Text(record.ratio, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(record.mimeType.substringAfter('/').uppercase(), style = MaterialTheme.typography.labelSmall)
            Text(android.text.format.DateFormat.format("dd MMM yyyy · HH:mm", record.createdAt).toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
