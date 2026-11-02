package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.HashtagGroup
import com.sdtech.studio.data.model.StudioDivision
import kotlinx.coroutines.flow.Flow

@Dao
interface HashtagGroupDao {
    @Query("SELECT * FROM hashtag_groups ORDER BY division ASC, name ASC")
    fun observeAll(): Flow<List<HashtagGroup>>

    @Query("SELECT * FROM hashtag_groups WHERE division = :division ORDER BY name ASC")
    fun observeByDivision(division: StudioDivision): Flow<List<HashtagGroup>>

    @Query("SELECT COUNT(*) FROM hashtag_groups")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HashtagGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: HashtagGroup)

    @Query("DELETE FROM hashtag_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM hashtag_groups")
    suspend fun deleteAll()
}
