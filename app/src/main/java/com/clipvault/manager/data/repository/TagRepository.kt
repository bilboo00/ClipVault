package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.TagDao
import com.clipvault.manager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val dao: TagDao
) {
    fun observeAll(): Flow<List<TagEntity>> = dao.observeAll()

    suspend fun getAll(): List<TagEntity> = dao.getAll()

    suspend fun create(name: String, color: String = "#4F46E5"): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return dao.insert(TagEntity(name = trimmed, color = color)).takeIf { it != -1L }
    }

    suspend fun update(tag: TagEntity) = dao.update(tag)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun setTagsForClip(clipId: Long, tagIds: List<Long>) =
        dao.setTagsForClip(clipId, tagIds)

    suspend fun getCrossRefsForClip(clipId: Long) = dao.getCrossRefsForClip(clipId)
}