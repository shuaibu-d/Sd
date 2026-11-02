package com.sdtech.studio.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ExportSharing {
    private fun stagedUri(context: Context, source: Uri, mimeType: String): Uri {
        val extension = when (mimeType) {
            "video/mp4" -> "mp4"
            "image/jpeg" -> "jpg"
            else -> "png"
        }
        val shareFile = File(context.cacheDir, "shared_exports/${System.currentTimeMillis()}.$extension")
        shareFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source)?.use { input -> shareFile.outputStream().use { output -> input.copyTo(output) } }
            ?: error("Export file is no longer available")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
    }

    fun open(context: Context, source: Uri, mimeType: String) {
        val contentUri = stagedUri(context, source, mimeType)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(contentUri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
    }

    fun share(context: Context, source: Uri, mimeType: String, caption: String = "") {
        val contentUri = stagedUri(context, source, mimeType)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your design").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
