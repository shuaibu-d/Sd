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
    version = 2,
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
    }
    abstract fun brandKitDao(): BrandKitDao
    abstract fun logoAssetDao(): LogoAssetDao
    abstract fun projectDao(): ProjectDao
    abstract fun exportRecordDao(): ExportRecordDao
    abstract fun captionTemplateDao(): CaptionTemplateDao
    abstract fun hashtagGroupDao(): HashtagGroupDao
    abstract fun plannerItemDao(): PlannerItemDao
}
