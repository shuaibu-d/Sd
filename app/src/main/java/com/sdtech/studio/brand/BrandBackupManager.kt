package com.sdtech.studio.brand

import android.content.Context
import android.net.Uri
import com.sdtech.studio.data.local.SDStudioDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.CaptionTemplate
import com.sdtech.studio.data.local.entity.HashtagGroup
import com.sdtech.studio.data.local.entity.LogoAsset
import com.sdtech.studio.data.model.LogoType
import com.sdtech.studio.data.model.PaletteColor
import com.sdtech.studio.data.model.StudioDivision
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable private data class BrandBackup(
    val brand: BrandBackupKit?,
    val captions: List<CaptionBackup>,
    val hashtags: List<HashtagBackup>,
    val logos: List<LogoBackup>
)
@Serializable private data class BrandBackupKit(
    val name: String, val slogan: String, val palette: List<PaletteColor>, val website: String,
    val whatsapp: String, val handle: String, val defaultCta: String, val brandLock: Boolean
)
@Serializable private data class CaptionBackup(val id: Long, val title: String, val body: String, val division: String)
@Serializable private data class HashtagBackup(val id: Long, val name: String, val tags: List<String>, val division: String)
@Serializable private data class LogoBackup(val type: String, val entry: String, val width: Int, val height: Int)

object BrandBackupManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(context: Context, database: SDStudioDatabase, destination: Uri) = withContext(Dispatchers.IO) {
        val brand = database.brandKitDao().getBrandKit()
        val captions = database.captionTemplateDao().observeAll()
        val captionList = captions.first()
        val hashtagList = database.hashtagGroupDao().observeAll().first()
        val logoList = database.logoAssetDao().observeAll().first()
        val logoDtos = logoList.mapIndexed { index, logo ->
            LogoBackup(logo.type.name, "logos/${logo.type.name.lowercase()}_$index.png", logo.width, logo.height)
        }
        val backup = BrandBackup(
            brand?.let { BrandBackupKit(it.name, it.slogan, it.palette, it.website, it.whatsapp, it.handle, it.defaultCta, it.brandLock) },
            captionList.map { CaptionBackup(it.id, it.title, it.body, it.division.name) },
            hashtagList.map { HashtagBackup(it.id, it.name, it.tags, it.division.name) }, logoDtos
        )
        context.contentResolver.openOutputStream(destination)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("brand-kit.json")); zip.write(json.encodeToString(backup).toByteArray()); zip.closeEntry()
                logoList.forEachIndexed { index, logo ->
                    val file = File(logo.filePath)
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry(logoDtos[index].entry)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        } ?: error("Unable to create the backup file.")
    }

    suspend fun import(context: Context, database: SDStudioDatabase, source: Uri) = withContext(Dispatchers.IO) {
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && !entry.name.startsWith("/") && !entry.name.contains("..")) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Unable to read the selected backup.")
        val manifest = entries["brand-kit.json"]?.decodeToString() ?: error("This ZIP does not contain a Brand Kit backup.")
        val backup = json.decodeFromString<BrandBackup>(manifest)
        database.withTransaction {
            database.captionTemplateDao().deleteAll()
            database.hashtagGroupDao().deleteAll()
            LogoType.entries.forEach { database.logoAssetDao().deleteByType(it) }
            backup.brand?.let { database.brandKitDao().save(BrandKit(
                name = it.name, slogan = it.slogan, palette = it.palette, website = it.website,
                whatsapp = it.whatsapp, handle = it.handle, defaultCta = it.defaultCta, brandLock = it.brandLock
            )) }
            backup.captions.forEach { database.captionTemplateDao().save(CaptionTemplate(it.id, it.title, it.body, StudioDivision.valueOf(it.division))) }
            backup.hashtags.forEach { database.hashtagGroupDao().save(HashtagGroup(it.id, it.name, it.tags, StudioDivision.valueOf(it.division))) }
            val folder = File(context.filesDir, "brand/logos").apply { mkdirs() }
            backup.logos.forEach { logo ->
                val bytes = entries[logo.entry] ?: return@forEach
                val type = LogoType.valueOf(logo.type)
                val file = File(folder, "${type.name.lowercase()}.png")
                file.writeBytes(bytes)
                database.logoAssetDao().deleteByType(type)
                database.logoAssetDao().insert(LogoAsset(type = type, filePath = file.absolutePath, width = logo.width, height = logo.height))
            }
        }
    }
}
