package com.sdtech.studio.data.local

import androidx.room.TypeConverter
import com.sdtech.studio.data.model.PaletteColor
import com.sdtech.studio.data.model.LogoType
import com.sdtech.studio.data.model.ProjectType
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.StudioDivision
import com.sdtech.studio.data.model.PlannerStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun paletteToJson(value: List<PaletteColor>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToPalette(value: String): List<PaletteColor> = json.decodeFromString(value)

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter fun logoTypeToString(value: LogoType): String = value.name
    @TypeConverter fun stringToLogoType(value: String): LogoType = LogoType.valueOf(value)
    @TypeConverter fun projectTypeToString(value: ProjectType): String = value.name
    @TypeConverter fun stringToProjectType(value: String): ProjectType = ProjectType.valueOf(value)
    @TypeConverter fun projectStatusToString(value: ProjectStatus): String = value.name
    @TypeConverter fun stringToProjectStatus(value: String): ProjectStatus = ProjectStatus.valueOf(value)
    @TypeConverter fun divisionToString(value: StudioDivision): String = value.name
    @TypeConverter fun stringToDivision(value: String): StudioDivision = StudioDivision.valueOf(value)
    @TypeConverter fun plannerStatusToString(value: PlannerStatus): String = value.name
    @TypeConverter fun stringToPlannerStatus(value: String): PlannerStatus = PlannerStatus.valueOf(value)
}
