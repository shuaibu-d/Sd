package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import android.graphics.Bitmap.createScaledBitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import com.sdtech.studio.render.*
import com.sdtech.studio.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

private object TemplateThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    fun get(key: String) = cache.get(key)
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

private val sampleValues = mapOf(
    "headline" to "Ideas into impact.",
    "body" to "Share useful ideas, practical advice and meaningful updates with your audience.",
    "quote" to "Great things are done by a series of small things brought together.",
    "author" to "Vincent van Gogh",
    "source" to "A reminder to keep moving forward.",
    "cta" to "Learn more",
    "brand" to "YOUR BRAND",
    "course" to "Build skills that matter",
    "level" to "ALL LEVELS",
    "duration" to "6 WEEKS",
    "initials" to "JD",
    "name" to "Jordan Davis",
    "project" to "A better digital experience",
    "description" to "Thoughtful design. Useful technology.",
    "year" to "2026",
    "number1" to "12K+",
    "label1" to "people reached",
    "number2" to "94%",
    "label2" to "positive feedback",
    "number3" to "250+",
    "label3" to "projects delivered",
    "footer" to "Growing together, every day.",
    "event" to "Ideas for the future",
    "person_role" to "Learner",
    "date" to "24 NOVEMBER",
    "time" to "10:00 AM  •  ONLINE",
    "venue" to "Virtual",
    "host" to "YOUR TEAM",
    "role" to "Creative Designer",
    "location" to "Remote",
    "type" to "Full-time",
    "series" to "THE KNOWLEDGE SERIES",
    "slide_number" to "01 / 05",
    "monogram" to "KA"
)

@Composable
fun TemplateGalleryScreen(onOpenTemplate: (String) -> Unit, onOpenDebug: () -> Unit = {}) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    var templates by remember { mutableStateOf(emptyList<Template>()) }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var darkTheme by remember { mutableStateOf(false) }
    var favourites by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) {
        selectedRatio = app.appContainer.settingsRepository.settings.first().lastUsedRatio
        templates = withContext(Dispatchers.IO) { TemplateRepository(context).loadAll() }
        val prefs = context.getSharedPreferences("template_gallery", android.content.Context.MODE_PRIVATE)
        favourites = prefs.getStringSet("favourites", emptySet()).orEmpty().toSet()
    }

    val categories = remember(templates) { listOf("All") + templates.map { it.category }.distinct().sorted() }
    val visible = remember(templates, selectedCategory, favourites) {
        templates.filter { selectedCategory == "All" || it.category == selectedCategory }
            .sortedWith(compareByDescending<Template> { it.id in favourites }.thenBy { it.name })
    }

    ScreenScaffold(scrollable = false) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Templates", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Choose a design and make it yours.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenDebug) { Text("Preview") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Canvas ratio", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark", style = MaterialTheme.typography.labelMedium)
                Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("1:1", "4:5", "9:16", "16:9").forEach { ratio ->
                FilterChip(selected = selectedRatio == ratio, onClick = { selectedRatio = ratio }, label = { Text(ratio) })
            }
        }
        Text("Categories", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            categories.forEach { category ->
                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category) })
            }
        }

        if (templates.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(visible, key = { it.id }) { template ->
                    TemplateGalleryCard(
                        template = template,
                        ratio = selectedRatio,
                        darkTheme = darkTheme,
                        favourite = template.id in favourites,
                        onFavourite = {
                            val updated = if (template.id in favourites) favourites - template.id else favourites + template.id
                            favourites = updated
                            context.getSharedPreferences("template_gallery", android.content.Context.MODE_PRIVATE)
                                .edit().putStringSet("favourites", updated).apply()
                        },
                        onClick = { onOpenTemplate(template.id) }
                    )
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
    favourite: Boolean,
    onFavourite: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val themeId = if (darkTheme) "dark" else "light"
    val cacheKey = "${template.id}|$ratio|$themeId"
    var bitmap by remember(cacheKey) { mutableStateOf(TemplateThumbnailCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val renderer = TemplateRenderer(context)
                    val full = renderer.render(
                        template,
                        TemplateContent(values = sampleValues),
                        ratio,
                        theme = template.themes.firstOrNull { it.id == themeId },
                        brandKit = null,
                        logos = emptyList()
                    )
                    val targetWidth = 360
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "${template.name} template preview",
                    modifier = Modifier.fillMaxWidth().aspectRatio(
                        when (ratio) { "4:5" -> .8f; "9:16" -> .5625f; "16:9" -> 1.7778f; else -> 1f }
                    )
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(
                        when (ratio) { "4:5" -> .8f; "9:16" -> .5625f; "16:9" -> 1.7778f; else -> 1f }
                    ).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(strokeWidth = 2.dp) }
            }
            IconButton(onClick = onFavourite, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(if (favourite) "★" else "☆", style = MaterialTheme.typography.titleLarge,
                    color = if (favourite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(template.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

