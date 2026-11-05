package com.sdtech.studio.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sdtech.studio.video.domain.MediaPersistenceGateway
import com.sdtech.studio.video.domain.MediaReferenceResolver
import java.io.File
import java.util.UUID

class AndroidMediaPersistenceGateway(
    private val context: Context
) : MediaPersistenceGateway {

    override fun isLocalFileOrAppPath(uriOrPath: String): Boolean {
        val trimmed = uriOrPath.trim()
        if (trimmed.startsWith("/") || trimmed.startsWith("file://")) return true
        if (!trimmed.contains("://")) return true
        return false
    }

    override fun tryTakePersistableReadPermission(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) return true
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    override fun copyToAppStorage(uriString: String, preferredExtension: String): String {
        val uri = Uri.parse(uriString)
        val mediaDir = File(context.filesDir, "project_media").apply { mkdirs() }
        val safeExt = preferredExtension.trimStart('.').ifBlank { "bin" }
        val destFile = File(mediaDir, "media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$safeExt")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Unable to read media source URI: $uriString")
        return Uri.fromFile(destFile).toString()
    }

    companion object {
        fun resolver(context: Context): MediaReferenceResolver =
            MediaReferenceResolver(AndroidMediaPersistenceGateway(context.applicationContext))
    }
}
