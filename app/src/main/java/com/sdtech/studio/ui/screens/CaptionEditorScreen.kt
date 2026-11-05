package com.sdtech.studio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionEditorScreen(projectId: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as SDStudioApplication
    val scope = rememberCoroutineScope()
    val project by app.appContainer.projectRepository.observeProject(projectId).collectAsStateWithLifecycle(initialValue = null)
    val captions by app.appContainer.contentLibraryRepository.captionTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    val hashtags by app.appContainer.contentLibraryRepository.hashtagGroups.collectAsStateWithLifecycle(initialValue = emptyList())
    val brand by app.appContainer.brandKitRepository.brandKit.collectAsStateWithLifecycle(initialValue = null)
    var text by remember(projectId) { mutableStateOf("") }
    var initialized by remember(projectId) { mutableStateOf(false) }
    var captionMenu by remember { mutableStateOf(false) }
    var hashtagMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(project?.id) {
        project?.let { text = it.caption; initialized = true }
    }
    LaunchedEffect(text, project?.id, initialized) {
        if (!initialized) return@LaunchedEffect
        delay(550)
        val current = project ?: return@LaunchedEffect
        app.appContainer.projectRepository.saveProject(current.copy(caption = text, updatedAt = System.currentTimeMillis()))
    }

    fun insert(value: String) {
        text = when {
            text.isBlank() -> value
            value.isBlank() -> text
            else -> text.trimEnd() + "\n\n" + value.trim()
        }
    }

    ScreenScaffold(title = "Caption", onBack = onBack) {
        SectionHeader(title = "Caption editor", subtitle = project?.title ?: "Attach reusable copy to this project")
        if (project == null) {
            Text("Loading project…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { captionMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Insert caption") }
                    DropdownMenu(expanded = captionMenu, onDismissRequest = { captionMenu = false }) {
                        captions.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = { insert(item.body); captionMenu = false }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { hashtagMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Insert hashtags") }
                    DropdownMenu(expanded = hashtagMenu, onDismissRequest = { hashtagMenu = false }) {
                        hashtags.forEach { group -> DropdownMenuItem(text = { Text(group.name) }, onClick = { insert(group.tags.joinToString(" ")); hashtagMenu = false }) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val details = listOfNotNull(brand?.website?.takeIf { it.isNotBlank() }, brand?.whatsapp?.takeIf { it.isNotBlank() }, brand?.handle?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    insert(details.ifBlank { "Add your contact details in Brand Kit." })
                }, modifier = Modifier.weight(1f)) { Text("Contact line") }
                OutlinedButton(onClick = { insert(brand?.defaultCta.orEmpty()) }, modifier = Modifier.weight(1f)) { Text("Call-to-action") }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CAPTION COPY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (text.isBlank()) Text("Write or assemble your caption…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    minLines = 9,
                    maxLines = 18
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${text.length} characters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Saved automatically", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Post caption", text))
                message = "Caption copied to clipboard."
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Copy caption") }
            OutlinedButton(onClick = { scope.launch { project?.let { app.appContainer.projectRepository.saveProject(it.copy(caption = text, updatedAt = System.currentTimeMillis())) }; onBack() } }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(it) }) }
}
