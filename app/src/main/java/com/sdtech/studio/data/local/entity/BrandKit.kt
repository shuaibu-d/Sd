package com.sdtech.studio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sdtech.studio.data.model.PaletteColor

@Entity(tableName = "brand_kits")
data class BrandKit(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val name: String,
    val slogan: String,
    val palette: List<PaletteColor>,
    val website: String,
    val whatsapp: String,
    val handle: String,
    val defaultCta: String,
    val brandLock: Boolean
) {
    companion object { const val SINGLE_ROW_ID = 1 }
}
