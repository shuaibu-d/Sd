package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.PlannerItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannerItemDao {
    @Query("SELECT * FROM planner_items ORDER BY scheduledDate ASC")
    fun observeAll(): Flow<List<PlannerItem>>

    @Query("SELECT * FROM planner_items WHERE status != 'POSTED' ORDER BY scheduledDate ASC")
    fun observeUnposted(): Flow<List<PlannerItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: PlannerItem): Long

    @Query("DELETE FROM planner_items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long)
}
