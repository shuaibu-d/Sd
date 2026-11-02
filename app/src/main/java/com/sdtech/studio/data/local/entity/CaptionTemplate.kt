package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sdtech.studio.data.model.StudioDivision

@Entity(tableName = "caption_templates")
data class CaptionTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val division: StudioDivision
)
