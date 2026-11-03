package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.Bitmap.createScaledBitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.render.Template
import com.sdtech.studio.render.TemplateContent
import com.sdtech.studio.render.TemplateRenderer
import com.sdtech.studio.render.TemplateRepository
import com.sdtech.studio.render.TemplateSamples
import com.sdtech.studio.ui.components.BrandScaffold
import com.sdtech.studio.ui.components.SDIcon
import com.sdtech.studio.ui.components.SDIconKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private object TemplateThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

private fun ratioValue(ratio: String): Float = when (ratio) { "4:5" -> 0.8f; "9:16" -> 0.5625f; "16:9" -> 1.7778f; else -> 1f }

@Composable
fun TemplateGalleryScreen(onOpenTemplate: (String) -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    var templates by remember { mutableStateOf(emptyList<Template>()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var darkTheme by remember { mutableStateOf(true) }
    var favourites by remember { mutableStateOf(emptySet<String>()) }
    val logos by app.appContainer.brandKitRepository.logoAssets.collectAsStateWithLifecycle(initialValue = emptyList())
    val brandKit by app.appContainer.brandKitRepository.brandKit.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(Unit) {
        runCatching { selectedRatio = app.appContainer.settingsRepository.settings.first().lastUsedRatio }
        templates = withContext(Dispatchers.IO) { runCatching { TemplateRepository(context).loadAll() }.getOrDefault(emptyList()) }
        loaded = true
        val prefs = context.getSharedPreferences("template_gallery", android.content.Context.MODE_PRIVATE)
        favourites = prefs.getStringSet("favourites", emptySet()).orEmpty().toSet()
    }

    val categories = remember(templates) { listOf("All") + templates.map { it.category }.distinct().sorted() }
    val visible = remember(templates, selectedCategory, favourites) {
        templates.filter { selectedCategory == "All" || it.category == selectedCategory }
            .sortedWith(compareByDescending<Template> { it.id in favourites }.thenBy { it.name })
    }

    BrandScaffold(title = "Posts", subtitle = "Pick a design and make it yours") {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1:1", "4:5", "9:16", "16:9").forEach { ratio ->
                    FilterChip(selected = selectedRatio == ratio, onClick = { selectedRatio = ratio }, label = { Text(ratio) })
                }
                FilterChip(selected = darkTheme, onClick = { darkTheme = true }, label = { Text("Dark") })
                FilterChip(selected = !darkTheme, onClick = { darkTheme = false }, label = { Text("Light") })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
                }
            }
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No templates could be loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
                ) {
                    items(visible, key = { it.id }) { template ->
                        TemplateGalleryCard(
                            template = template, ratio = selectedRatio, darkTheme = darkTheme,
                            logoKey = logos.joinToString { "${it.type}${it.id}" }, favourite = template.id in favourites,
                            onFavourite = {
                                val updated = if (template.id in favourites) favourites - template.id else favourites + template.id
                                favourites = updated
                                context.getSharedPreferences("template_gallery", android.content.Context.MODE_PRIVATE)
                                    .edit().putStringSet("favourites", updated).apply()
                            },
                            onClick = { onOpenTemplate(template.id) },
                            logos = logos, brandKit = brandKit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateGalleryCard(
    template: Template, ratio: String, darkTheme: Boolean, logoKey: String, favourite: Boolean,
    onFavourite: () -> Unit, onClick: () -> Unit,
    logos: List<com.sdtech.studio.data.local.entity.LogoAsset>, brandKit: com.sdtech.studio.data.local.entity.BrandKit?
) {
    val context = LocalContext.current
    val themeId = if (darkTheme) "dark" else "light"
    val cacheKey = "${template.id}|$ratio|$themeId|$logoKey"
    var bitmap by remember(cacheKey) { mutableStateOf(TemplateThumbnailCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val full = TemplateRenderer(context).render(
                        template, TemplateContent(values = TemplateSamples.values), ratio,
                        theme = template.themes.firstOrNull { it.id == themeId } ?: template.themes.firstOrNull(),
                        brandKit = brandKit, logos = logos
                    )
                    val targetWidth = 420
                    val targetHeight = (targetWidth * full.height.toFloat() / full.width).toInt().coerceAtLeast(1)
                    val small = createScaledBitmap(full, targetWidth, targetHeight, true)
                    if (small !== full) full.recycle()
                    TemplateThumbnailCache.put(cacheKey, small)
                    small
                }.getOrNull()
            }
        }
    }

    Card(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            val shown = bitmap
            if (shown != null) {
                Image(shown.asImageBitmap(), contentDescription = "${template.name} preview",
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratioValue(ratio)))
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(ratioValue(ratio)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
            IconButton(onClick = onFavourite, modifier = Modifier.align(Alignment.TopEnd)) {
                SDIcon(if (favourite) SDIconKind.Star else SDIconKind.StarOutline,
                    tint = if (favourite) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color.White, size = 22.dp)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
            Text(template.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
