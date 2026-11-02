package com.sdtech.studio.data.repository

import com.sdtech.studio.data.local.dao.ProjectDao
import com.sdtech.studio.data.local.entity.Project
import com.sdtech.studio.data.model.ProjectStatus
import com.sdtech.studio.data.model.ProjectType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectRepositoryTest {
    @Test
    fun saveProjectPublishesProjectToFlow() = runBlocking {
        val fakeDao = FakeProjectDao()
        val repository = ProjectRepository(fakeDao)
        val project = Project(
            id = "project-test-1",
            type = ProjectType.POST,
            title = "Launch announcement",
            ratio = "1:1",
            status = ProjectStatus.DRAFT,
            payloadJson = "{}",
            createdAt = 100L,
            updatedAt = 100L
        )

        repository.saveProject(project)

        assertEquals(project, repository.projects.first().single())
        assertNotNull(repository.getProject(project.id))
    }

    private class FakeProjectDao : ProjectDao {
        private val state = MutableStateFlow<List<Project>>(emptyList())

        override fun observeAll(): Flow<List<Project>> = state

        override fun observeById(projectId: String): Flow<Project?> =
            MutableStateFlow(state.value.firstOrNull { it.id == projectId })

        override suspend fun getById(projectId: String): Project? =
            state.value.firstOrNull { it.id == projectId }

        override suspend fun count(): Int = state.value.size

        override suspend fun save(project: Project) {
            state.value = state.value.filterNot { it.id == project.id } + project
        }

        override suspend fun deleteById(projectId: String) {
            state.value = state.value.filterNot { it.id == projectId }
        }
    }
}
