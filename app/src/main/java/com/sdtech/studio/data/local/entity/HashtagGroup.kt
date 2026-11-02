package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sdtech.studio.data.model.StudioDivision

@Entity(tableName = "hashtag_groups")
data class HashtagGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tags: List<String>,
    val division: StudioDivision
)
