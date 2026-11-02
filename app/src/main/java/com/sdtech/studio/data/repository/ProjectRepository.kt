package com.sdtech.studio.data.repository

import com.sdtech.studio.data.local.dao.ProjectDao
import com.sdtech.studio.data.local.entity.Project
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val dao: ProjectDao) {
    val projects: Flow<List<Project>> = dao.observeAll()
    fun observeProject(id: String): Flow<Project?> = dao.observeById(id)
    suspend fun getProject(id: String): Project? = dao.getById(id)
    suspend fun saveProject(project: Project) = dao.save(project)
    suspend fun deleteProject(id: String) = dao.deleteById(id)
}
