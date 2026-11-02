package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sdtech.studio.data.model.LogoType

@Entity(tableName = "logo_assets")
data class LogoAsset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LogoType,
    val filePath: String,
    val width: Int,
    val height: Int
)
