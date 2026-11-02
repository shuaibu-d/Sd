package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.ProjectType
import java.util.UUID

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: ProjectType,
    val title: String,
    val ratio: String,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val payloadJson: String = "{}",
    val createdAt: Long,
    val updatedAt: Long,
    val thumbnailPath: String? = null,
    val caption: String = ""
)
