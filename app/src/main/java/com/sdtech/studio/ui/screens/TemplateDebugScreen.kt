package com.sdtech.studio.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.render.RenderOptions
import com.sdtech.studio.render.TemplateContent
import com.sdtech.studio.render.TemplateRenderer
import com.sdtech.studio.render.TemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TemplateDebugScreen() {
    val context = LocalContext.current
    var showGuides by remember { mutableStateOf(false) }
    val app = remember(context) { context.applicationContext as SDStudioApplication }
    val brandKit by app.appContainer.brandKitRepository.brandKit.collectAsState(initial = null)
    val logos by app.appContainer.brandKitRepository.logoAssets.collectAsState(initial = emptyList())
    val previews by produceState<List<Pair<String, Bitmap>>>(initialValue = emptyList(), showGuides, brandKit, logos) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val template = TemplateRepository(context).load("announcement")
                    ?: error("Sample template is missing")
                val renderer = TemplateRenderer(context)
                listOf("1:1", "4:5", "9:16", "16:9").map { ratio ->
                    ratio to renderer.render(
                        template,
                        TemplateContent(values = mapOf(
                            "headline" to "Ideas into impact.",
                            "body" to "We build useful digital products, practical AI and skills for what comes next."
                        )),
                        ratio,
                        options = RenderOptions(showSafeZones = showGuides),
                        brandKit = brandKit,
                        logos = logos
                    )
                }
            }.getOrElse { emptyList() }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Template renderer", style = MaterialTheme.typography.headlineMedium)
        Text("One JSON template reflows across all four canvas ratios.",
            style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Safe-zone guides", modifier = Modifier.weight(1f))
            Switch(checked = showGuides, onCheckedChange = { showGuides = it })
        }
        if (previews.isEmpty()) {
            Text("Unable to render the sample template. Check the template JSON and renderer logs.")
        } else {
            previews.forEach { (ratio, bitmap) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ratio, style = MaterialTheme.typography.titleMedium)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Rendered sample template in $ratio ratio",
                            modifier = Modifier.fillMaxWidth().background(
                                MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)
                            )
                        )
                    }
                }
            }
        }
    }
}
