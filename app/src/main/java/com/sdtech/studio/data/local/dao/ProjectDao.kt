package com.sdtech.studio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sdtech.studio.data.local.entity.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeById(projectId: String): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getById(projectId: String): Project?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(project: Project)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: String)
}
