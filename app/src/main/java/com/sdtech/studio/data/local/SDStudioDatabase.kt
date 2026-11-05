package com.sdtech.studio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.sdtech.studio.data.local.dao.BrandKitDao
import com.sdtech.studio.data.local.dao.CaptionTemplateDao
import com.sdtech.studio.data.local.dao.ExportRecordDao
import com.sdtech.studio.data.local.dao.HashtagGroupDao
import com.sdtech.studio.data.local.dao.LogoAssetDao
import com.sdtech.studio.data.local.dao.PlannerItemDao
import com.sdtech.studio.data.local.dao.ProjectDao
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.CaptionTemplate
import com.sdtech.studio.data.local.entity.ExportRecord
import com.sdtech.studio.data.local.entity.HashtagGroup
import com.sdtech.studio.data.local.entity.LogoAsset
import com.sdtech.studio.data.local.entity.PlannerItem
import com.sdtech.studio.data.local.entity.Project

@Database(
    entities = [
        BrandKit::class,
        LogoAsset::class,
        Project::class,
        ExportRecord::class,
        CaptionTemplate::class,
        HashtagGroup::class,
        PlannerItem::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class SDStudioDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN caption TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE planner_items ADD COLUMN reminderAtMillis INTEGER")
                db.execSQL("ALTER TABLE planner_items ADD COLUMN logoVisible INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planner_items ADD COLUMN textReadable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planner_items ADD COLUMN captionHashtagsAdded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planner_items ADD COLUMN callToActionAdded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE planner_items ADD COLUMN safeZoneChecked INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN documentVersion INTEGER NOT NULL DEFAULT 1")
                val cursor = db.query("SELECT id, type, ratio, payloadJson, createdAt, updatedAt FROM projects")
                val updates = mutableListOf<Triple<String, String, Int>>()
                cursor.use { c ->
                    val idIdx = c.getColumnIndexOrThrow("id")
                    val typeIdx = c.getColumnIndexOrThrow("type")
                    val ratioIdx = c.getColumnIndexOrThrow("ratio")
                    val payloadIdx = c.getColumnIndexOrThrow("payloadJson")
                    val createdIdx = c.getColumnIndexOrThrow("createdAt")
                    val updatedIdx = c.getColumnIndexOrThrow("updatedAt")
                    while (c.moveToNext()) {
                        val id = c.getString(idIdx)
                        val type = c.getString(typeIdx)
                        val ratio = c.getString(ratioIdx) ?: "9:16"
                        val payloadJson = c.getString(payloadIdx) ?: "{}"
                        val createdAt = c.getLong(createdIdx)
                        val updatedAt = c.getLong(updatedIdx)
                        if (type == "VIDEO" && payloadJson.isNotBlank() && payloadJson != "{}") {
                            val migratedJson = com.sdtech.studio.video.domain.ProjectDocumentMigrator.migrateV1JsonToV2Json(
                                projectId = id,
                                canvasRatio = ratio,
                                payloadJson = payloadJson,
                                createdAtMs = createdAt,
                                updatedAtMs = updatedAt
                            )
                            updates.add(Triple(id, migratedJson, 2))
                        }
                    }
                }
                for ((id, migratedJson, docVersion) in updates) {
                    db.execSQL(
                        "UPDATE projects SET payloadJson = ?, documentVersion = ? WHERE id = ?",
                        arrayOf<Any>(migratedJson, docVersion, id)
                    )
                }
            }
        }
    }
    abstract fun brandKitDao(): BrandKitDao
    abstract fun logoAssetDao(): LogoAssetDao
    abstract fun projectDao(): ProjectDao
    abstract fun exportRecordDao(): ExportRecordDao
    abstract fun captionTemplateDao(): CaptionTemplateDao
    abstract fun hashtagGroupDao(): HashtagGroupDao
    abstract fun plannerItemDao(): PlannerItemDao
}
