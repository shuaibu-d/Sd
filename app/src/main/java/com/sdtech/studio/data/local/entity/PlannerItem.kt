package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sdtech.studio.data.model.PlannerStatus

@Entity(
    tableName = "planner_items",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("projectId"), Index("scheduledDate")]
)
data class PlannerItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String? = null,
    val title: String,
    val caption: String,
    val platform: String,
    val scheduledDate: Long,
    val status: PlannerStatus = PlannerStatus.IDEA,
    val reminderAtMillis: Long? = null,
    val logoVisible: Boolean = false,
    val textReadable: Boolean = false,
    val captionHashtagsAdded: Boolean = false,
    val callToActionAdded: Boolean = false,
    val safeZoneChecked: Boolean = false
)
