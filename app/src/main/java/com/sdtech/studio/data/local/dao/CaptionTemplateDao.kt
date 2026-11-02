package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.CaptionTemplate
import com.sdtech.studio.data.model.StudioDivision
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionTemplateDao {
    @Query("SELECT * FROM caption_templates ORDER BY division ASC, title ASC")
    fun observeAll(): Flow<List<CaptionTemplate>>

    @Query("SELECT * FROM caption_templates WHERE division = :division ORDER BY title ASC")
    fun observeByDivision(division: StudioDivision): Flow<List<CaptionTemplate>>

    @Query("SELECT COUNT(*) FROM caption_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CaptionTemplate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: CaptionTemplate)

    @Query("DELETE FROM caption_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM caption_templates")
    suspend fun deleteAll()
}
