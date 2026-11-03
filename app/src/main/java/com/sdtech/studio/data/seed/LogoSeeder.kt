package com.sdtech.studio.data.seed

import android.content.Context
import android.graphics.BitmapFactory
import com.sdtech.studio.data.local.SDStudioDatabase
import com.sdtech.studio.data.local.entity.LogoAsset
import com.sdtech.studio.data.model.LogoType
import kotlinx.coroutines.flow.first
import java.io.File

/** Installs the official SD Technologies logo variants the first time the app runs, so every post and video has a logo. */
object LogoSeeder {
    private val files = mapOf(
        LogoType.FULL to "logo_full_light.png",
        LogoType.ICON to "logo_icon_light.png",
        LogoType.LIGHT to "logo_full_dark.png",
        LogoType.DARK to "logo_full_light.png"
    )

    suspend fun seedIfMissing(context: Context, database: SDStudioDatabase) {
        val dao = database.logoAssetDao()
        val existing = dao.observeAll().first()
        if (existing.isNotEmpty()) return
        val folder = File(context.filesDir, "brand/logos").apply { mkdirs() }
        files.forEach { (type, assetName) ->
            runCatching {
                val target = File(folder, "${type.name.lowercase()}.png")
                context.assets.open("brand/$assetName").use { input -> target.outputStream().use { input.copyTo(it) } }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.absolutePath, bounds)
                dao.insert(LogoAsset(type = type, filePath = target.absolutePath, width = bounds.outWidth, height = bounds.outHeight))
            }
        }
    }
}
