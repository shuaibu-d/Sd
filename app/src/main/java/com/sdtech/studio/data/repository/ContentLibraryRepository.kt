package com.sdtech.studio.data.repository

import com.sdtech.studio.data.local.dao.CaptionTemplateDao
import com.sdtech.studio.data.local.dao.HashtagGroupDao
import com.sdtech.studio.data.local.entity.CaptionTemplate
import com.sdtech.studio.data.local.entity.HashtagGroup
import com.sdtech.studio.data.model.StudioDivision
import kotlinx.coroutines.flow.Flow

class ContentLibraryRepository(
    private val captionDao: CaptionTemplateDao,
    private val hashtagDao: HashtagGroupDao
) {
    val captionTemplates: Flow<List<CaptionTemplate>> = captionDao.observeAll()
    val hashtagGroups: Flow<List<HashtagGroup>> = hashtagDao.observeAll()
    fun captionsFor(division: StudioDivision): Flow<List<CaptionTemplate>> = captionDao.observeByDivision(division)
    fun hashtagsFor(division: StudioDivision): Flow<List<HashtagGroup>> = hashtagDao.observeByDivision(division)

    suspend fun saveCaption(item: CaptionTemplate) = captionDao.save(item)
    suspend fun deleteCaption(id: Long) = captionDao.deleteById(id)
    suspend fun saveHashtag(item: HashtagGroup) = hashtagDao.save(item)
    suspend fun deleteHashtag(id: Long) = hashtagDao.deleteById(id)
}
