package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.R
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.performance.CentralBitmapPool
import com.sdtech.studio.performance.RenderDispatcher
import com.sdtech.studio.render.RenderOptions
import com.sdtech.studio.render.Template
import com.sdtech.studio.render.TemplateContent
import com.sdtech.studio.render.TemplateRenderer
import com.sdtech.studio.render.TemplateRepository
import com.sdtech.studio.render.TemplateSamples
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.components.ScreenScaffold
import com.sdtech.studio.ui.theme.CloudWhite
import com.sdtech.studio.ui.theme.DarkSurface
import com.sdtech.studio.ui.theme.DeepNavy
import com.sdtech.studio.ui.theme.InnovationTeal
import com.sdtech.studio.ui.theme.JetBrainsMono
import com.sdtech.studio.ui.theme.MidnightNavy
import com.sdtech.studio.ui.theme.PremiumGold
import com.sdtech.studio.ui.theme.SlateGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val brandKit by app.appContainer.brandKitRepository.brandKit.collectAsStateWithLifecycle(initialValue = null)
    var featuredTemplates by remember { mutableStateOf(emptyList<Template>()) }

    LaunchedEffect(Unit) {
        featuredTemplates = withContext(Dispatchers.IO) {
            runCatching { TemplateRepository(context).loadAll().take(6) }.getOrDefault(emptyList())
        }
    }

    ScreenScaffold(statusBarPadding = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_sd_mark), null, Modifier.size(38.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SD STUDIO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground, letterSpacing = 1.2.sp)
                Text("Creative production workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenPlanner) { SDIcon(SDIconKind.Calendar, tint = MaterialTheme.colorScheme.onBackground) }
            IconButton(onClick = onOpenSettings) { SDIcon(SDIconKind.Settings, tint = MaterialTheme.colorScheme.onBackground) }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF183A50), DeepNavy))).border(1.dp, InnovationTeal.copy(alpha = .28f), RoundedCornerShape(22.dp)).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("STUDIO WORKSPACE", fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelSmall, color = PremiumGold)
                    Box(Modifier.size(5.dp).clip(CircleShape).background(InnovationTeal))
                    Text("READY", fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelSmall, color = InnovationTeal)
                }
                Text("Create work\nworth sharing.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = CloudWhite, lineHeight = 34.sp)
                Text("Design campaign graphics, produce branded videos and manage every creative asset in one focused workspace.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD1DCE7))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCreatePost, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = MidnightNavy)) {
                        SDIcon(SDIconKind.Posts, tint = MidnightNavy, size = 18.dp); Spacer(Modifier.width(7.dp)); Text("Create post", fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onCreateVideo, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = InnovationTeal, contentColor = MidnightNavy)) {
                        SDIcon(SDIconKind.Video, tint = MidnightNavy, size = 18.dp); Spacer(Modifier.width(7.dp)); Text("Create video", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("WORKSPACES", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("TOOLS & LIBRARIES", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile("Brand system", "Identity assets", SDIconKind.Brand, onOpenBrand, Modifier.weight(1f))
            QuickTile("Content plan", "Schedule work", SDIconKind.Calendar, onOpenPlanner, Modifier.weight(1f))
            QuickTile("Library", "Saved content", SDIconKind.Library, onOpenLibrary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Template library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Curated starting points for professional content", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onCreatePost) { Text("Explore", color = InnovationTeal) }
        }
        Spacer(Modifier.height(10.dp))
        if (featuredTemplates.isEmpty()) {
            Text("Preparing your template library…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                featuredTemplates.forEach { template -> HomeTemplatePreviewChip(template, logos, brandKit, onCreatePost) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recent projects", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Pick up where your team left off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (projects.isNotEmpty()) TextButton(onClick = onOpenLibrary) { Text("View all", color = InnovationTeal) }
        }
        Spacer(Modifier.height(10.dp))
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Your project space is clear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("New posts and videos will appear here, with drafts saved to your library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else projects.take(5).forEach { project ->
            ProjectRow(project, onClick = { onOpenProject(project) })
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HomeTemplatePreviewChip(
    template: Template,
    logos: List<com.sdtech.studio.data.local.entity.LogoAsset>,
    brandKit: com.sdtech.studio.data.local.entity.BrandKit?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val logoKey = remember(logos) { logos.joinToString { "${it.type}${it.id}" } }
    val cacheKey = "home_thumb:${template.id}|1:1|dark|$logoKey|320"
    var bitmap by remember(cacheKey) { mutableStateOf<Bitmap?>(CentralBitmapPool.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        val cached = CentralBitmapPool.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            bitmap = cached
            return@LaunchedEffect
        }
        val rendered = runCatching {
            RenderDispatcher.withRenderContext {
                CentralBitmapPool.getOrPut(cacheKey) {
                    TemplateRenderer(context).render(
                        template = template,
                        content = TemplateContent(values = TemplateSamples.values),
                        ratio = "1:1",
                        theme = template.themes.firstOrNull { it.id == "dark" } ?: template.themes.firstOrNull(),
                        options = RenderOptions(maxDimension = 320),
                        brandKit = brandKit,
                        logos = logos
                    )
                }
            }
        }.getOrNull()
        if (rendered != null) {
            bitmap = rendered
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.width(136.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .background(DeepNavy),
                contentAlignment = Alignment.Center
            ) {
                val shown = bitmap
                if (shown != null && !shown.isRecycled) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = "${template.name} template preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = template.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = InnovationTeal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    subtitle: String,
    icon: SDIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(InnovationTeal.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                SDIcon(icon, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        if (isVideo) PremiumGold.copy(alpha = 0.18f) else InnovationTeal.copy(alpha = 0.18f),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                SDIcon(
                    if (isVideo) SDIconKind.Video else SDIconKind.Posts,
                    tint = if (isVideo) PremiumGold else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${if (isVideo) "Video" else "Post"} \u2022 ${project.ratio} \u2022 $whenText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}
