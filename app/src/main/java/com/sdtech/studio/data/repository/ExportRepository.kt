package com.sdtech.studio.data.repository

import com.sdtech.studio.data.local.dao.ExportRecordDao
import com.sdtech.studio.data.local.entity.ExportRecord
import kotlinx.coroutines.flow.Flow

class ExportRepository(private val dao: ExportRecordDao) {
    val exports: Flow<List<ExportRecord>> = dao.observeAll()
    fun exportsForProject(projectId: String): Flow<List<ExportRecord>> = dao.observeByProject(projectId)
    suspend fun recordExport(record: ExportRecord): Long = dao.insert(record)
    suspend fun deleteExport(id: Long) = dao.deleteById(id)
}
