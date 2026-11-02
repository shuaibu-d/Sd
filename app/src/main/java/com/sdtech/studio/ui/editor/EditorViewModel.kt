package com.sdtech.studio.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.render.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class EditorSlide(
    val id: String = UUID.randomUUID().toString(),
    val values: Map<String, String> = emptyMap(),
    val imagePaths: Map<String, String> = emptyMap(),
    val imageFits: Map<String, String> = emptyMap(),
    val imageOverlays: Map<String, Float> = emptyMap(),
    val textPresets: Map<String, String> = emptyMap()
)

@Serializable
data class EditorDraft(
    val slides: List<EditorSlide> = listOf(EditorSlide()),
    val activeSlide: Int = 0,
    val ratio: String = "1:1",
    val darkTheme: Boolean = false,
    val logoPlacement: String = "SAFE",
    val logoVariant: String = "FULL",
    val showSafeZones: Boolean = false,
    val brandLock: Boolean = true
)

data class EditorUiState(
    val template: Template? = null,
    val draft: EditorDraft = EditorDraft(),
    val isSaving: Boolean = false,
    val lastSavedAt: Long? = null,
    val error: String? = null
) {
    val activeSlide: EditorSlide get() = draft.slides.getOrElse(draft.activeSlide) { draft.slides.first() }
}

class EditorViewModel(application: Application, private val templateId: String) : AndroidViewModel(application) {
    private val app = application as SDStudioApplication
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val projectId = "template-draft-$templateId"
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private val undoStack = ArrayDeque<EditorDraft>()
    private val redoStack = ArrayDeque<EditorDraft>()
    private var saveJob: Job? = null

    init { viewModelScope.launch { restore() } }

    private suspend fun restore() {
        val template = withContext(Dispatchers.IO) { TemplateRepository(getApplication()).load(templateId) }
        val saved = app.appContainer.projectRepository.getProject(projectId)
        val preferences = app.appContainer.settingsRepository.settings.first()
        val draft = saved?.let { runCatching { json.decodeFromString<EditorDraft>(it.payloadJson) }.getOrNull() }
            ?: EditorDraft(ratio = preferences.lastUsedRatio, logoPlacement = preferences.defaultLogoMode,
                slides = listOf(EditorSlide(values = template?.let(::defaultValues).orEmpty())))
        _state.value = EditorUiState(template = template, draft = draft)
        if (saved == null && template != null) saveNow()
    }

    private fun defaultValues(template: Template): Map<String, String> {
        val defaults = mapOf("headline" to "Ideas into impact.", "body" to "Share useful ideas, practical advice and meaningful updates.",
            "cta" to "Learn more", "quote" to "Great things are done by a series of small things brought together.",
            "author" to "Your name", "brand" to "YOUR BRAND", "course" to "Build skills that matter", "name" to "Your name",
            "project" to "A better digital experience", "description" to "Thoughtful design. Useful technology.",
            "event" to "Ideas for the future", "role" to "Creative Designer", "monogram" to "KA", "slide_number" to "01 / 05")
        val keys = Regex("\\{([A-Za-z0-9_.-]+)}")
        val values = template.layers.filterIsInstance<TemplateLayer.Text>().flatMap { keys.findAll(it.text).map { m -> m.groupValues[1] } }
            .distinct().associateWith { defaults[it] ?: it.replace('_', ' ').replaceFirstChar(Char::uppercase) }.toMutableMap()
        template.layers.filterIsInstance<TemplateLayer.Text>().forEach { layer ->
            values[layer.id] = Regex("\\{([A-Za-z0-9_.-]+)}").replace(layer.text) { m -> values[m.groupValues[1]] ?: m.value }
        }
        return values
    }

    private fun mutate(transform: (EditorDraft) -> EditorDraft) {
        val old = _state.value.draft
        undoStack.addLast(old); while (undoStack.size > 20) undoStack.removeFirst()
        redoStack.clear()
        _state.value = _state.value.copy(draft = transform(old))
        scheduleSave()
    }

    fun setText(key: String, value: String) = mutate { d -> d.copy(slides = d.slides.mapIndexed { i, s -> if (i == d.activeSlide) s.copy(values = s.values + (key to value.take(4_000))) else s }) }
    fun setRatio(value: String) = mutate { it.copy(ratio = value) }
    fun setDark(value: Boolean) = mutate { it.copy(darkTheme = value) }
    fun setLogoPlacement(value: String) = mutate { it.copy(logoPlacement = value) }
    fun setLogoVariant(value: String) = mutate { it.copy(logoVariant = value) }
    fun setSafeZones(value: Boolean) = mutate { it.copy(showSafeZones = value) }
    fun setBrandLock(value: Boolean) = mutate { it.copy(brandLock = value) }
    fun setImageFit(slot: String, fit: String) = mutate { d -> updateSlide(d) { it.copy(imageFits = it.imageFits + (slot to fit)) } }
    fun setImageOverlay(slot: String, amount: Float) = mutate { d -> updateSlide(d) { it.copy(imageOverlays = it.imageOverlays + (slot to amount)) } }
    fun setTextPreset(layerId: String, preset: String) = mutate { d -> updateSlide(d) { it.copy(textPresets = it.textPresets + (layerId to preset)) } }

    private fun updateSlide(d: EditorDraft, change: (EditorSlide) -> EditorSlide): EditorDraft =
        d.copy(slides = d.slides.mapIndexed { i, slide -> if (i == d.activeSlide) change(slide) else slide })

    fun importImage(uri: Uri, slot: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = File(getApplication<Application>().filesDir, "editor_media/${UUID.randomUUID()}.img")
                file.parentFile?.mkdirs()
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    ?: error("Unable to read selected image")
                withContext(Dispatchers.Main) { mutate { d -> updateSlide(d) { it.copy(imagePaths = it.imagePaths + (slot to file.absolutePath)) } } }
            }.onFailure { withContext(Dispatchers.Main) { _state.value = _state.value.copy(error = it.message) } }
        }
    }

    fun selectSlide(index: Int) = mutate { it.copy(activeSlide = index.coerceIn(0, it.slides.lastIndex)) }
    fun addSlide() = mutate { d -> if (d.slides.size >= 10) d else d.copy(slides = d.slides + d.activeSlide.let { d.slides[it].copy(id = UUID.randomUUID().toString()) }, activeSlide = d.slides.size) }
    fun duplicateSlide() = mutate { d -> if (d.slides.size >= 10) d else d.copy(slides = d.slides + d.activeSlide.let { d.slides[it].copy(id = UUID.randomUUID().toString()) }, activeSlide = d.slides.size) }
    fun deleteSlide() = mutate { d -> if (d.slides.size <= 1) d else d.copy(slides = d.slides.filterIndexed { i, _ -> i != d.activeSlide }, activeSlide = (d.activeSlide - 1).coerceAtLeast(0)) }
    fun moveSlide(from: Int, to: Int) = mutate { d -> if (from !in d.slides.indices || to !in d.slides.indices) d else {
        val list = d.slides.toMutableList(); val item = list.removeAt(from); list.add(to, item)
        d.copy(slides = list, activeSlide = to)
    } }
    fun undo() { if (undoStack.isNotEmpty()) { redoStack.addLast(_state.value.draft); _state.value = _state.value.copy(draft = undoStack.removeLast()); scheduleSave() } }
    fun redo() { if (redoStack.isNotEmpty()) { undoStack.addLast(_state.value.draft); _state.value = _state.value.copy(draft = redoStack.removeLast()); scheduleSave() } }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1800); saveNow() }
    }

    fun saveNow() {
        val snapshot = _state.value
        val template = snapshot.template ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isSaving = true)
            runCatching {
                val existing = app.appContainer.projectRepository.getProject(projectId)
                app.appContainer.projectRepository.saveProject(Project(
                    id = projectId, type = ProjectType.POST, title = template.name, ratio = snapshot.draft.ratio,
                    status = ProjectStatus.DRAFT, payloadJson = json.encodeToString(snapshot.draft),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
                    caption = existing?.caption.orEmpty()
                ))
            }.onSuccess { withContext(Dispatchers.Main) { _state.value = _state.value.copy(isSaving = false, lastSavedAt = System.currentTimeMillis()) } }
                .onFailure { withContext(Dispatchers.Main) { _state.value = _state.value.copy(isSaving = false, error = it.message) } }
        }
    }

    companion object {
        fun factory(application: Application, templateId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(application, templateId) as T
        }
    }
}

private object EditorImageCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(path: String): Bitmap? = cache.get(path) ?: runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        bitmap?.also { cache.put(path, it) }
    }.getOrNull()
}

fun EditorSlide.toTemplateContent(brandLock: Boolean = true): TemplateContent {
    val bitmaps = imagePaths.mapNotNull { (slot, path) -> EditorImageCache.get(path)?.let { slot to it } }.toMap()
    return TemplateContent(values = values, images = bitmaps, imageFits = imageFits.mapValues { if (it.value == "FIT") ImageFit.FIT else ImageFit.CROP }, imageOverlays = imageOverlays, textPresets = textPresets, brandLock = brandLock)
}
