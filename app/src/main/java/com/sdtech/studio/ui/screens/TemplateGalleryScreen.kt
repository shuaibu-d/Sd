package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.performance.CentralBitmapPool
import com.sdtech.studio.performance.ErrorBoundary
import com.sdtech.studio.performance.MemoryGuard
import com.sdtech.studio.performance.RenderDispatcher
import com.sdtech.studio.performance.ThermalGuard
import com.sdtech.studio.render.RenderOptions
import com.sdtech.studio.render.Template
import com.sdtech.studio.render.TemplateContent
import com.sdtech.studio.render.TemplateRenderer
import com.sdtech.studio.render.TemplateRepository
import com.sdtech.studio.render.TemplateSamples
import com.sdtech.studio.ui.components.BrandScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import com.sdtech.studio.ui.theme.CloudWhite
import com.sdtech.studio.ui.theme.DeepNavy
import com.sdtech.studio.ui.theme.InnovationTeal
import com.sdtech.studio.ui.theme.JetBrainsMono
import com.sdtech.studio.ui.theme.MidnightNavy
import com.sdtech.studio.ui.theme.PremiumGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private fun ratioValue(ratio: String): Float = when (ratio) {
    "4:5" -> 0.8f
    "9:16" -> 0.5625f
    "16:9" -> 1.7778f
    else -> 1f
}

@Composable
fun TemplateGalleryScreen(onOpenTemplate: (String) -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val repo = remember(context) { TemplateRepository(context) }
    var templates by remember { mutableStateOf(emptyList<Template>()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var darkTheme by remember { mutableStateOf(true) }
    var favourites by remember { mutableStateOf(emptySet<String>()) }
    val logos by app.appContainer.brandKitRepository.logoAssets.collectAsStateWithLifecycle(initialValue = emptyList())
    val brandKit by app.appContainer.brandKitRepository.brandKit.collectAsStateWithLifecycle(initialValue = null)
    val memoryPressure by MemoryGuard.pressureLevel.collectAsStateWithLifecycle()
    val thermalStatus by ThermalGuard.status.collectAsStateWithLifecycle()
    val thumbnailWidthPx = remember(memoryPressure, thermalStatus) {
        ThermalGuard.effectiveGalleryThumbnailWidthPx
    }

    LaunchedEffect(reloadToken) {
        loaded = false
        loadError = null
        runCatching { selectedRatio = app.appContainer.settingsRepository.settings.first().lastUsedRatio }
        val result = withContext(Dispatchers.IO) { runCatching { repo.loadAll() } }
        result.onSuccess { loadedTemplates ->
            templates = loadedTemplates
            loadError = if (loadedTemplates.isEmpty()) "No valid templates were found in app/src/main/assets/templates." else null
        }.onFailure { error ->
            loadError = error.message ?: "Template assets could not be read."
        }
        loaded = true
        val prefs = context.getSharedPreferences("template_gallery", android.content.Context.MODE_PRIVATE)
        favourites = prefs.getStringSet("favourites", emptySet()).orEmpty().toSet()
    }

    val categories by remember(templates) {
        derivedStateOf { listOf("All") + templates.map { it.category }.distinct().sorted() }
    }
    val categoryTemplates by remember(templates, selectedCategory) {
        derivedStateOf { templates.filter { selectedCategory == "All" || it.category == selectedCategory } }
    }
    val ratioMatches by remember(categoryTemplates, selectedRatio) {
        derivedStateOf { categoryTemplates.filter { it.supportedRatios.isEmpty() || selectedRatio in it.supportedRatios } }
    }
    // If a ratio has no explicitly tagged matches, keep the gallery useful: render all
    // category templates into the selected canvas ratio instead of presenting a blank page.
    val visible by remember(ratioMatches, categoryTemplates, favourites) {
        derivedStateOf {
            (ratioMatches.ifEmpty { categoryTemplates })
                .sortedWith(compareByDescending<Template> { it.id in favourites }.thenBy { it.name })
        }
    }
    val logoKey by remember(logos) {
        derivedStateOf { logos.joinToString { "${it.type}${it.id}" } }
    }

    BrandScaffold(
        title = "Template library",
        subtitle = "${visible.size} designs  •  Brand-ready creative assets"
    ) {
        Column(Modifier.fillMaxSize()) {
            // Studio aspect ratio & theme control bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("1:1", "4:5", "9:16", "16:9").forEach { ratio ->
                    FilterChip(
                        selected = selectedRatio == ratio,
                        onClick = { selectedRatio = ratio },
                        label = {
                            Text(
                                text = ratio,
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InnovationTeal.copy(alpha = 0.2f),
                            selectedLabelColor = InnovationTeal
                        ),
                        modifier = Modifier.testTag("gallery_ratio_$ratio")
                    )
                }
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = darkTheme,
                    onClick = { darkTheme = true },
                    label = { Text("Dark Navy") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PremiumGold.copy(alpha = 0.2f),
                        selectedLabelColor = PremiumGold
                    )
                )
                FilterChip(
                    selected = !darkTheme,
                    onClick = { darkTheme = false },
                    label = { Text("Cloud Light") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PremiumGold.copy(alpha = 0.2f),
                        selectedLabelColor = PremiumGold
                    )
                )
            }

            // Category filter strip
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        modifier = Modifier.testTag("gallery_category_$category")
                    )
                }
            }

            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = InnovationTeal)
                }
            } else if (templates.isEmpty() || visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = loadError ?: "No templates support $selectedRatio in this category.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (templates.isNotEmpty()) TextButton(onClick = { selectedCategory = "All"; selectedRatio = "1:1" }) { Text("Reset filters") }
                        TextButton(onClick = { repo.invalidate(); templates = emptyList(); reloadToken++ }) { Text("Reload templates") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("template_gallery_grid"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    items(
                        items = visible,
                        key = { it.id },
                        contentType = { "template_card" }
                    ) { template ->
                        TemplateGalleryCard(
                            template = template,
                            ratio = selectedRatio,
                            darkTheme = darkTheme,
                            logoKey = logoKey,
                            thumbnailMaxEdge = thumbnailWidthPx,
                            favourite = template.id in favourites,
                            onFavourite = {
                                val updated = if (template.id in favourites) favourites - template.id else favourites + template.id
                                favourites = updated
                                context.getSharedPreferences("template_gallery", android.content.Context.MODE_PRIVATE)
                                    .edit().putStringSet("favourites", updated).apply()
                            },
                            onClick = { onOpenTemplate(template.id) },
                            logos = logos,
                            brandKit = brandKit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateGalleryCard(
    template: Template,
    ratio: String,
    darkTheme: Boolean,
    logoKey: String,
    thumbnailMaxEdge: Int,
    favourite: Boolean,
    onFavourite: () -> Unit,
    onClick: () -> Unit,
    logos: List<com.sdtech.studio.data.local.entity.LogoAsset>,
    brandKit: com.sdtech.studio.data.local.entity.BrandKit?
) {
    val context = LocalContext.current
    val themeId = if (darkTheme) "dark" else "light"
    val cacheKey = "thumb:${template.id}|$ratio|$themeId|$logoKey|$thumbnailMaxEdge"
    var retryToken by remember { mutableIntStateOf(0) }
    var renderFailed by remember(cacheKey) { mutableStateOf(false) }
    var bitmap by remember(cacheKey) { mutableStateOf(CentralBitmapPool.get(cacheKey)) }

    LaunchedEffect(cacheKey, retryToken) {
        val cached = CentralBitmapPool.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            bitmap = cached
            return@LaunchedEffect
        }
        val rendered = ErrorBoundary.runGuarded(
            source = "TemplateGallery:${template.id}",
            title = "Template preview issue",
            message = "Could not render the preview for ${template.name}. Tap Retry to try again.",
            onRetry = { retryToken++ }
        ) {
            RenderDispatcher.withRenderContext {
                CentralBitmapPool.getOrPut(cacheKey) {
                    TemplateRenderer(context).render(
                        template = template,
                        content = TemplateContent(values = TemplateSamples.values),
                        ratio = ratio,
                        theme = template.themes.firstOrNull { it.id == themeId } ?: template.themes.firstOrNull(),
                        options = RenderOptions(maxDimension = thumbnailMaxEdge),
                        brandKit = brandKit,
                        logos = logos
                    )
                }
            }
        }
        if (rendered != null) {
            bitmap = rendered
            renderFailed = false
        } else {
            renderFailed = true
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.testTag("template_card_${template.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp)
                .background(DeepNavy),
            contentAlignment = Alignment.Center
        ) {
            val shown = bitmap
            if (shown != null && !shown.isRecycled) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = "${template.name} preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .aspectRatio(
                            ratioValue(ratio),
                            matchHeightConstraintsFirst = ratio == "9:16" || ratio == "4:5"
                        )
                )
            } else if (renderFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = CloudWhite)
                    TextButton(onClick = { renderFailed = false; retryToken++ }) {
                        Text("Retry", color = InnovationTeal)
                    }
                }
            } else {
                CircularProgressIndicator(strokeWidth = 2.dp, color = InnovationTeal, modifier = Modifier.size(24.dp))
            }

            // Ratio badge top-left
            Surface(
                color = MidnightNavy.copy(alpha = 0.78f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = ratio,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = CloudWhite,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Favourite button top-right (48dp touch target)
            IconButton(
                onClick = onFavourite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MidnightNavy.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    SDIcon(
                        if (favourite) SDIconKind.Star else SDIconKind.StarOutline,
                        tint = if (favourite) PremiumGold else CloudWhite,
                        size = 18.dp,
                        contentDescription = if (favourite) "Remove ${template.name} from favourites" else "Add ${template.name} to favourites"
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                template.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    template.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = InnovationTeal,
                    maxLines = 1
                )
                Text(
                    "1080p \u2022 ${template.themes.size} themes",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
