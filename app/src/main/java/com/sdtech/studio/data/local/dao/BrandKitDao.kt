package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.BrandKit
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandKitDao {
    @Query("SELECT * FROM brand_kits WHERE id = 1 LIMIT 1")
    fun observeBrandKit(): Flow<BrandKit?>

    @Query("SELECT * FROM brand_kits WHERE id = 1 LIMIT 1")
    suspend fun getBrandKit(): BrandKit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(brandKit: BrandKit)
}
