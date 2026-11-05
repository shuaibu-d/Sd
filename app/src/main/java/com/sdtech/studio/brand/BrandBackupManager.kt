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
        // Read and validate the complete archive before touching the current database.
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val name = entry.name.replace('\\', '/')
                    require(!name.startsWith('/') &&
                        name.split('/').none { it == ".." || it.isBlank() }) {
                        "The selected ZIP contains an unsafe file path."
                    }
                    require(name !in entries) { "The selected ZIP contains duplicate files." }
                    val limit = if (name == "brand-kit.json") 2L * 1024 * 1024 else 20L * 1024 * 1024
                    val bytes = zip.readBytes()
                    require(bytes.size.toLong() <= limit) { "A file in the backup is too large: $name" }
                    totalBytes += bytes.size
                    require(totalBytes <= 60L * 1024 * 1024) { "The backup is larger than the 60 MB import limit." }
                    entries[name] = bytes
                    zip.closeEntry()
                }
            }
        } ?: error("Unable to open the selected file. Choose a valid SD Studio backup ZIP.")

        val manifestBytes = entries["brand-kit.json"]
            ?: error("This file is not an SD Studio Brand Kit backup: brand-kit.json is missing.")
        val backup = runCatching { json.decodeFromString<BrandBackup>(manifestBytes.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("The backup manifest is invalid or damaged: ${it.message}") }

        // Validate enum values and referenced logo files before deleting or replacing anything.
        backup.captions.forEach { require(runCatching { StudioDivision.valueOf(it.division) }.isSuccess) { "Unknown caption division: ${it.division}" } }
        backup.hashtags.forEach { require(runCatching { StudioDivision.valueOf(it.division) }.isSuccess) { "Unknown hashtag division: ${it.division}" } }
        backup.logos.forEach { logo ->
            require(runCatching { LogoType.valueOf(logo.type) }.isSuccess) { "Unknown logo type: ${logo.type}" }
            require(logo.width > 0 && logo.height > 0) { "Invalid dimensions for logo ${logo.entry}." }
            require(logo.entry.startsWith("logos/") && entries[logo.entry] != null) { "A logo image is missing from the backup: ${logo.entry}" }
        }

        val logoFolder = File(context.filesDir, "brand/logos").apply { mkdirs() }
        val staged = backup.logos.map { logo ->
            val type = LogoType.valueOf(logo.type)
            val bytes = entries.getValue(logo.entry)
            val temp = File(logoFolder, "${type.name.lowercase()}_${java.util.UUID.randomUUID()}.tmp")
            temp.writeBytes(bytes)
            Triple(logo, type, temp)
        }

        try {
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
                staged.groupBy { it.second }.forEach { (type, assets) ->
                    assets.forEachIndexed { index, (logo, _, temp) ->
                        val target = File(logoFolder, "${type.name.lowercase()}_${index}_${System.currentTimeMillis()}.png")
                        check(temp.renameTo(target)) { "Could not install logo image ${logo.entry}." }
                        database.logoAssetDao().insert(LogoAsset(type = type, filePath = target.absolutePath, width = logo.width, height = logo.height))
                    }
                }
            }
        } finally {
            staged.forEach { it.third.delete() }
        }
    }
}
