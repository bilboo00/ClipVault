package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.CollectionDao
import com.clipvault.manager.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val dao: CollectionDao
) {
    fun observeAll(): Flow<List<CollectionEntity>> = dao.observeAll()

    suspend fun getAll(): List<CollectionEntity> = dao.getAll()

    suspend fun create(name: String, icon: String = "folder"): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return dao.insert(CollectionEntity(name = trimmed, icon = icon)).takeIf { it != -1L }
    }

    suspend fun update(collection: CollectionEntity) = dao.update(collection)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun setCollectionsForClip(clipId: Long, collectionIds: List<Long>) =
        dao.setCollectionsForClip(clipId, collectionIds)

    suspend fun getCrossRefsForClip(clipId: Long) = dao.getCrossRefsForClip(clipId)
}