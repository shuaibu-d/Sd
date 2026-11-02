package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "export_records",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class ExportRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val filePath: String,
    val mimeType: String,
    val ratio: String,
    val createdAt: Long
)
