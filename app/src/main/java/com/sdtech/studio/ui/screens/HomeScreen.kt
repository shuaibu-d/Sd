package com.sdtech.studio.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.R
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.theme.MidnightNavy
import com.sdtech.studio.ui.theme.PremiumGold
import com.sdtech.studio.ui.theme.InnovationTeal

@Composable
fun HomeScreen(
    onCreatePost: () -> Unit,
    onCreateVideo: () -> Unit,
    onOpenPlanner: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBrand: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenProject: (Project) -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val projects by app.appContainer.projectRepository.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val logos by app.appContainer.brandKitRepository.logoAssets.collectAsStateWithLifecycle(initialValue = emptyList())

    ScreenScaffold {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).background(MidnightNavy, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.ic_sd_mark), contentDescription = "SD Technologies logo", modifier = Modifier.size(width = 30.dp, height = 27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SD Studio", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text("Create Without Limits.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenPlanner) { SDIcon(SDIconKind.Calendar, tint = MaterialTheme.colorScheme.onBackground) }
            IconButton(onClick = onOpenSettings) { SDIcon(SDIconKind.Settings, tint = MaterialTheme.colorScheme.onBackground) }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1D3A56), Color(0xFF123047), MidnightNavy)), RoundedCornerShape(28.dp))
                .border(1.dp, Color(0x3316A6A0), RoundedCornerShape(28.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Make something great today", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    "Branded posts and voice-over explainer videos, with your logo placed automatically.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9D3E0)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onCreatePost, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = InnovationTeal, contentColor = MidnightNavy)
                    ) { SDIcon(SDIconKind.Posts, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("New post") }
                    Button(
                        onClick = onCreateVideo, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = MidnightNavy)
                    ) { SDIcon(SDIconKind.Video, size = 18.dp); Spacer(Modifier.width(8.dp)); Text("New video") }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Brand Kit", SDIconKind.Brand, onOpenBrand, Modifier.weight(1f))
            QuickTile("Planner", SDIconKind.Calendar, onOpenPlanner, Modifier.weight(1f))
            QuickTile("Library", SDIconKind.Library, onOpenLibrary, Modifier.weight(1f))
        }

        if (logos.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add your logo in Brand Kit so it appears on every post and video.", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick = onOpenBrand) { Text("Open") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recent projects", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (projects.size > 4) TextButton(onClick = onOpenLibrary) { Text("See all") }
        }
        Spacer(Modifier.height(8.dp))
        if (projects.isEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your next great idea starts here", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Pick a template or start a video. Your drafts are saved automatically and will appear here.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            projects.take(4).forEach { project ->
                ProjectRow(project, onClick = { onOpenProject(project) })
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun QuickTile(label: String, icon: SDIconKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SDIcon(icon, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ProjectRow(project: Project, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val isVideo = project.type == ProjectType.VIDEO
    val whenText = android.text.format.DateUtils.getRelativeTimeSpanString(
        project.updatedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(
                    if (isVideo) PremiumGold.copy(alpha = 0.18f) else InnovationTeal.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                SDIcon(if (isVideo) SDIconKind.Video else SDIconKind.Posts, tint = if (isVideo) PremiumGold else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${if (isVideo) "Video" else "Post"} \u2022 ${project.ratio} \u2022 $whenText",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}
