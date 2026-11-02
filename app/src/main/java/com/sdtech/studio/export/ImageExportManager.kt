package com.sdtech.studio.export

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import androidx.core.content.ContextCompat
import com.sdtech.studio.SDStudioApplication
import com.sdtech.studio.data.local.entity.ExportRecord
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.render.*
import com.sdtech.studio.ui.editor.EditorDraft
import com.sdtech.studio.ui.editor.toTemplateContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageExportManager(private val context: Context) {
    data class Request(val ratios: List<String>, val jpeg: Boolean = false)
    data class Result(val uris: List<Uri>, val caption: String)

    suspend fun export(templateId: String, draft: EditorDraft, request: Request, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        require(request.ratios.isNotEmpty()) { "Choose at least one size." }
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Storage permission is required on Android 8 and 9 to save to Pictures/SD Studio.")
        }
        val app = context.applicationContext as SDStudioApplication
        val template = TemplateRepository(context).load(templateId) ?: error("Template not found")
        val projectId = "template-draft-$templateId"
        val brandKit = app.appContainer.database.brandKitDao().getBrandKit()
        val logos = app.appContainer.database.logoAssetDao().observeAll().first()
        val saved = app.appContainer.projectRepository.getProject(projectId)
        val caption = saved?.caption?.takeIf { it.isNotBlank() }
            ?: draft.slides.getOrNull(draft.activeSlide)?.values?.entries
                ?.firstOrNull { it.key.contains("caption", true) || it.key.equals("body", true) }?.value.orEmpty()
        val now = System.currentTimeMillis()
        app.appContainer.projectRepository.saveProject(
            saved?.copy(
                title = template.name, ratio = draft.ratio, status = ProjectStatus.DRAFT,
                payloadJson = Json { encodeDefaults = true }.encodeToString(draft), updatedAt = now
            ) ?: Project(
                id = projectId, type = ProjectType.POST, title = template.name, ratio = draft.ratio,
                status = ProjectStatus.DRAFT, payloadJson = Json { encodeDefaults = true }.encodeToString(draft),
                createdAt = now, updatedAt = now
            )
        )
        val uris = mutableListOf<Uri>()
        val slides = draft.slides
        val carouselMode = slides.size > 1 || template.id.contains("carousel", ignoreCase = true)
        val total = request.ratios.size * slides.size
        var completed = 0
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        request.ratios.forEach { ratio ->
            slides.forEachIndexed { slideIndex, slide ->
                var bitmap: Bitmap? = null
                try {
                    val counter = "%02d / %02d".format(slideIndex + 1, slides.size)
                    val layerCounters = template.layers.filterIsInstance<TemplateLayer.Text>()
                        .filter { it.text.contains("{slide_number}") }.associate { it.id to counter }
                    val content = slide.toTemplateContent(draft.brandLock).let {
                        it.copy(values = it.values + ("slide_number" to counter) + layerCounters)
                    }
                    bitmap = TemplateRenderer(context).render(
                        template, content, ratio,
                        theme = template.themes.firstOrNull { it.id == if (draft.darkTheme) "dark" else "light" },
                        options = RenderOptions(
                            logoPlacementMode = if (draft.logoPlacement == "TIGHT") LogoPlacementMode.TIGHT else LogoPlacementMode.SAFE,
                            logoTypeOverride = draft.logoVariant,
                            brandColors = brandKit?.palette.orEmpty().associate { it.name.lowercase() to it.hex }
                        ), brandKit = brandKit, logos = logos
                    )
                    val extension = if (request.jpeg) "jpg" else "png"
                    val slideSuffix = if (carouselMode) "_slide_${(slideIndex + 1).toString().padStart(2, '0')}" else ""
                    val name = "SDStudio_${safeName(template.name)}_${ratio.replace(':', 'x')}${slideSuffix}_$timestamp.$extension"
                    val outputUri = saveBitmap(bitmap, name, request.jpeg)
                    app.appContainer.exportRepository.recordExport(ExportRecord(
                        projectId = projectId, filePath = outputUri.toString(),
                        mimeType = if (request.jpeg) "image/jpeg" else "image/png", ratio = ratio,
                        createdAt = System.currentTimeMillis()
                    ))
                    uris += outputUri
                    completed++
                    withContext(Dispatchers.Main) { onProgress(completed, total) }
                } finally {
                    bitmap?.takeIf { !it.isRecycled }?.recycle()
                }
            }
        }
        Result(uris, caption)
    }

    private fun saveBitmap(bitmap: Bitmap, name: String, jpeg: Boolean): Uri {
        val resolver = context.contentResolver
        val mime = if (jpeg) "image/jpeg" else "image/png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SD Studio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create image in Pictures/SD Studio")
            try {
                resolver.openOutputStream(uri, "w")?.use { stream ->
                    val format = if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    check(bitmap.compress(format, if (jpeg) 92 else 100, stream)) { "Image encoding failed" }
                } ?: error("Unable to open export destination")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                return uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SD Studio")
        if (!directory.exists() && !directory.mkdirs()) error("Unable to create Pictures/SD Studio")
        val file = File(directory, name)
        FileOutputStream(file).use { stream ->
            val format = if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            check(bitmap.compress(format, if (jpeg) 92 else 100, stream)) { "Image encoding failed" }
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(file)
    }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
}
