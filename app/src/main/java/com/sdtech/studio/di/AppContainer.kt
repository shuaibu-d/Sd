package com.sdtech.studio.di

import android.content.Context
import androidx.room.Room
import com.sdtech.studio.data.local.SDStudioDatabase.Companion.MIGRATION_1_2
import com.sdtech.studio.data.local.SDStudioDatabase
import com.sdtech.studio.data.repository.BrandKitRepository
import com.sdtech.studio.data.repository.ContentLibraryRepository
import com.sdtech.studio.data.repository.ExportRepository
import com.sdtech.studio.data.repository.PlannerRepository
import com.sdtech.studio.data.repository.ProjectRepository
import com.sdtech.studio.data.seed.FirstLaunchSeeder
import com.sdtech.studio.data.settings.SettingsRepository

class AppContainer(context: Context) {
    val database: SDStudioDatabase = Room.databaseBuilder(
        context.applicationContext,
        SDStudioDatabase::class.java,
        "sd_studio.db"
    ).addMigrations(MIGRATION_1_2).build()

    val settingsRepository = SettingsRepository(context)
    val brandKitRepository = BrandKitRepository(database.brandKitDao(), database.logoAssetDao())
    val projectRepository = ProjectRepository(database.projectDao())
    val contentLibraryRepository = ContentLibraryRepository(
        database.captionTemplateDao(),
        database.hashtagGroupDao()
    )
    val exportRepository = ExportRepository(database.exportRecordDao())
    val plannerRepository = PlannerRepository(database.plannerItemDao())
    val firstLaunchSeeder = FirstLaunchSeeder(database, settingsRepository)
}
