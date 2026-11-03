package com.sdtech.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.ui.components.BrandScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import kotlinx.coroutines.launch

@Composable
fun VideoHubScreen(onNewVideo: () -> Unit, onOpenVideo: (String) -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val scope = rememberCoroutineScope()
    val projects by app.appContainer.projectRepository.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val videos = projects.filter { it.type == ProjectType.VIDEO }

    BrandScaffold(title = "Videos", subtitle = "Voice-over explainers with your logo") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onNewVideo, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                SDIcon(SDIconKind.Plus, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("New explainer video", style = MaterialTheme.typography.titleSmall)
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How it works", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("1. Paste your script and split it into scenes.\n2. Record your voice-over scene by scene.\n3. Preview, then export. Your logo is added to the top-right corner automatically.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Your videos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
            if (videos.isEmpty()) {
                Text("No videos yet. Tap \u201cNew explainer video\u201d to start.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            videos.forEach { video ->
                ProjectRow(video, onClick = { onOpenVideo(video.id) }, trailing = {
                    IconButton(onClick = { scope.launch { app.appContainer.projectRepository.deleteProject(video.id) } }) {
                        SDIcon(SDIconKind.Trash, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
