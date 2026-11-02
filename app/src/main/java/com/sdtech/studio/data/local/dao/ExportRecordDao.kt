package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.ExportRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportRecordDao {
    @Query("SELECT * FROM export_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportRecord>>

    @Query("SELECT * FROM export_records WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeByProject(projectId: String): Flow<List<ExportRecord>>

    @Query("DELETE FROM export_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExportRecord): Long
}
