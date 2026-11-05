package com.sdtech.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.ui.components.BrandScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.theme.CloudWhite
import com.sdtech.studio.ui.theme.DeepNavy
import com.sdtech.studio.ui.theme.InnovationTeal
import com.sdtech.studio.ui.theme.JetBrainsMono
import com.sdtech.studio.ui.theme.MidnightNavy
import com.sdtech.studio.ui.theme.PremiumGold
import kotlinx.coroutines.launch

@Composable
fun VideoHubScreen(onNewVideo: () -> Unit, onOpenVideo: (String) -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val scope = rememberCoroutineScope()
    val projects by app.appContainer.projectRepository.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val videos by remember(projects) { derivedStateOf { projects.filter { it.type == ProjectType.VIDEO } } }

    BrandScaffold(title = "Video studio", subtitle = "Production workspace  /  ${videos.size} projects") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF183A50), DeepNavy))).border(1.dp, InnovationTeal.copy(alpha = .28f), RoundedCornerShape(22.dp)).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudioFeaturePill("MULTI-TRACK", InnovationTeal)
                        StudioFeaturePill("FRAME ACCURATE", PremiumGold)
                        StudioFeaturePill("BRAND SAFE", InnovationTeal)
                    }
                    Text("Video production", style = MaterialTheme.typography.headlineSmall, color = CloudWhite, fontWeight = FontWeight.Bold)
                    Text("Build explainers, campaigns and social films with a visual timeline, layered media, voice-over and consistent brand placement.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD1DCE7))
                    Button(onClick = onNewVideo, Modifier.fillMaxWidth().height(52.dp).testTag("new_video_project_button"), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = InnovationTeal, contentColor = MidnightNavy)) {
                        SDIcon(SDIconKind.Plus, size = 19.dp, tint = MidnightNavy); Spacer(Modifier.width(8.dp)); Text("New video project", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VideoMetric("PROJECTS", videos.size.toString(), Modifier.weight(1f))
                VideoMetric("TIMELINE", "Multi-track", Modifier.weight(1f))
                VideoMetric("OUTPUT", "HD ready", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Production library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Recent edits and active projects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("LOCAL PROJECTS", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (videos.isEmpty()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No productions yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Create a project to open the editing workspace. Your timelines and drafts will be listed here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else videos.forEach { video ->
                key(video.id) {
                    ProjectRow(video, onClick = { onOpenVideo(video.id) }, trailing = {
                        IconButton(onClick = { scope.launch { app.appContainer.projectRepository.deleteProject(video.id) } }, modifier = Modifier.size(48.dp)) {
                            SDIcon(SDIconKind.Trash, tint = MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "Delete ${video.title}")
                        }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VideoMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun StudioFeaturePill(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMono,
            color = accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
