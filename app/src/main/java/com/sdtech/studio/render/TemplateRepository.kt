package com.sdtech.studio.render

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

class TemplateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    private fun loadFolder(folder: String): List<Template> =
        appContext.assets.list(folder).orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
            .sorted()
            .mapNotNull { name ->
                runCatching {
                    appContext.assets.open("$folder/$name").bufferedReader().use { reader ->
                        json.decodeFromString<Template>(reader.readText())
                    }
                }.onFailure { Log.e("TemplateRepository", "Could not read $folder/$name", it) }.getOrNull()
            }

    fun loadAll(): List<Template> = synchronized(CACHE) {
        // Never permanently cache an empty result: a transient asset/decoder failure must
        // not leave the gallery blank for the lifetime of the process.
        val cached = CACHE.posts
        if (!cached.isNullOrEmpty()) cached
        else loadFolder("templates").also { CACHE.posts = it }
    }

    fun invalidate() = synchronized(CACHE) {
        CACHE.posts = null
        CACHE.scenes = null
    }

    fun load(id: String): Template? = loadAll().firstOrNull { it.id == id }

    fun loadSceneTemplates(): List<Template> = synchronized(CACHE) {
        CACHE.scenes ?: loadFolder("video_scenes").also { CACHE.scenes = it }
    }

    private object CACHE {
        var posts: List<Template>? = null
        var scenes: List<Template>? = null
    }
}
