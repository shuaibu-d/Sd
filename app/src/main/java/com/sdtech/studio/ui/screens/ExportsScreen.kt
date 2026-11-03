package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
fun ExportsContent(onEditAgain: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ExportRecord>>(emptyList()) }
    var selected by remember { mutableStateOf<ExportRecord?>(null) }
    LaunchedEffect(Unit) { app.appContainer.exportRepository.exports.collectLatest { records = it } }

    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No exports yet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("Your exported posts and videos will appear here.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(records, key = { it.id }) { record -> ExportCard(record) { selected = record } }
        }
    }

    selected?.let { record ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${record.ratio} \u00b7 ${record.mimeType.substringAfter('/').uppercase()}", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { runCatching { ExportSharing.open(context, Uri.parse(record.filePath), record.mimeType) } },
                    modifier = Modifier.fillMaxWidth()) { Text("Open") }
                OutlinedButton(onClick = { runCatching { ExportSharing.share(context, Uri.parse(record.filePath), record.mimeType) } },
                    modifier = Modifier.fillMaxWidth()) { Text("Share") }
                if (record.projectId.startsWith("template-draft-")) {
                    OutlinedButton(onClick = {
                        val templateId = record.projectId.removePrefix("template-draft-")
                        selected = null
                        onEditAgain(templateId)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Edit again") }
                }
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
                        }
                        runCatching { app.appContainer.exportRepository.deleteExport(record.id) }
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
    val isVideo = record.mimeType.startsWith("video")
    LaunchedEffect(record.filePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(record.filePath)
                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try { retriever.setDataSource(context, uri); retriever.getFrameAtTime(0) } finally { retriever.release() }
                } else {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    if (uri.scheme == "file") BitmapFactory.decodeFile(uri.path, options)
                    else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
            }.getOrNull()
        }
    }
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = bitmap
            Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (shown != null) Image(shown.asImageBitmap(), contentDescription = "Exported design", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${record.ratio} \u00b7 ${if (isVideo) "VIDEO" else record.mimeType.substringAfter('/').uppercase()}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(android.text.format.DateFormat.format("dd MMM yyyy \u00b7 HH:mm", record.createdAt).toString(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
