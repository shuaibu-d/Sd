package com.sdtech.studio.data.repository

import com.sdtech.studio.data.local.dao.PlannerItemDao
import com.sdtech.studio.data.local.entity.PlannerItem
import kotlinx.coroutines.flow.Flow

class PlannerRepository(private val dao: PlannerItemDao) {
    val items: Flow<List<PlannerItem>> = dao.observeAll()
    val unpostedItems: Flow<List<PlannerItem>> = dao.observeUnposted()
    suspend fun save(item: PlannerItem): Long = dao.save(item)
    suspend fun delete(id: Long) = dao.deleteById(id)
}
