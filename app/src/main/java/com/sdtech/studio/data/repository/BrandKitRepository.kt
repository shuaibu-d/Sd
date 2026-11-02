package com.sdtech.studio.data.repository

import com.sdtech.studio.data.local.dao.BrandKitDao
import com.sdtech.studio.data.local.dao.LogoAssetDao
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.LogoAsset
import kotlinx.coroutines.flow.Flow

class BrandKitRepository(
    private val brandKitDao: BrandKitDao,
    private val logoAssetDao: LogoAssetDao
) {
    val brandKit: Flow<BrandKit?> = brandKitDao.observeBrandKit()
    val logoAssets: Flow<List<LogoAsset>> = logoAssetDao.observeAll()

    suspend fun saveBrandKit(value: BrandKit) = brandKitDao.save(value)
    suspend fun addLogoAsset(value: LogoAsset): Long = logoAssetDao.insert(value)
    suspend fun deleteLogoAsset(id: Long) = logoAssetDao.deleteById(id)
    suspend fun replaceLogoAsset(value: LogoAsset) {
        logoAssetDao.deleteByType(value.type)
        logoAssetDao.insert(value)
    }
}
