package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.LogoAsset
import kotlinx.coroutines.flow.Flow

@Dao
interface LogoAssetDao {
    @Query("SELECT * FROM logo_assets ORDER BY id DESC")
    fun observeAll(): Flow<List<LogoAsset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: LogoAsset): Long

    @Query("DELETE FROM logo_assets WHERE type = :type")
    suspend fun deleteByType(type: com.sdtech.studio.data.model.LogoType)

    @Query("DELETE FROM logo_assets WHERE id = :assetId")
    suspend fun deleteById(assetId: Long)
}
