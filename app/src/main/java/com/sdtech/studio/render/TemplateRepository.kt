package com.sdtech.studio.render

import android.content.Context
import kotlinx.serialization.json.Json

class TemplateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    fun loadAll(): List<Template> = appContext.assets.list("templates")
        .orEmpty().filter { it.endsWith(".json", ignoreCase = true) }
        .sorted().map { name ->
            appContext.assets.open("templates/$name").bufferedReader().use { reader ->
                json.decodeFromString<Template>(reader.readText())
            }
        }

    fun load(id: String): Template? = loadAll().firstOrNull { it.id == id }

    fun loadSceneTemplates(): List<Template> = appContext.assets.list("video_scenes")
        .orEmpty().filter { it.endsWith(".json", ignoreCase = true) }
        .sorted().map { name ->
            appContext.assets.open("video_scenes/$name").bufferedReader().use { reader ->
                json.decodeFromString<Template>(reader.readText())
            }
        }
}
