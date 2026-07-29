package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardRepository @Inject constructor(
    private val dao: ClipDao
) {
    fun observeAll(): Flow<List<Clip>> =
        dao.observeAll().map { list -> list.map(Clip.Companion::fromEntity) }

    fun observeByType(type: ClipType): Flow<List<Clip>> =
        dao.observeByType(type.name).map { list -> list.map(Clip.Companion::fromEntity) }

    fun search(query: String): Flow<List<Clip>> =
        dao.search(query).map { list -> list.map(Clip.Companion::fromEntity) }

    fun observeById(id: Long): Flow<Clip?> =
        dao.observeById(id).map { entity -> entity?.let(Clip.Companion::fromEntity) }

    suspend fun saveIfNew(content: String, sourceLabel: String? = null): Long? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        if (dao.countByContent(trimmed) > 0) return null
        val entity = ClipEntity(
            content = trimmed,
            type = ClipClassifier.classify(trimmed),
            sourceLabel = sourceLabel
        )
        return dao.insert(entity).takeIf { it != -1L }
    }

    suspend fun saveImage(imagePath: String, sourceLabel: String? = null): Long? {
        val entity = ClipEntity(
            content = "[Image]",
            type = ClipType.IMAGE,
            sourceLabel = sourceLabel,
            imageUri = imagePath
        )
        return dao.insert(entity).takeIf { it != -1L }
    }

    suspend fun togglePin(id: Long, pinned: Boolean) {
        val newSortOrder = if (pinned) dao.minPinnedSortOrder() - 1 else 0
        dao.setPinned(id, pinned)
        dao.setSortOrder(id, newSortOrder)
    }

    suspend fun bulkSetPinned(ids: List<Long>, pinned: Boolean): Int {
        val order = if (pinned) dao.minPinnedSortOrder() - 1 else 0
        val updated = dao.setPinnedBulk(ids, pinned)
        dao.setSortOrderBulk(ids, order)
        return updated
    }

    suspend fun reorderPinned(orderedIds: List<Long>) {
        dao.reorderPinnedWithOrders(orderedIds.mapIndexed { index, id -> id to index }.toMap())
    }

    suspend fun bulkDelete(ids: List<Long>): Int = dao.deleteByIds(ids)
    suspend fun getByIds(ids: List<Long>): List<ClipEntity> = dao.getByIds(ids)
    suspend fun toggleFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun updateText(id: Long, content: String) {
        dao.getById(id)?.let { entity ->
            dao.update(entity.copy(content = content.trim()))
        }
    }

    suspend fun setNotes(id: Long, notes: String?) {
        dao.setNotes(id, notes?.trim()?.takeIf { it.isNotEmpty() })
    }

    suspend fun setExpiration(id: Long, expiresAt: Long?) = dao.setExpiresAt(id, expiresAt)

    suspend fun setUseLimit(id: Long, useLimit: Int?) = dao.setUseLimit(id, useLimit)

    suspend fun incrementUseCount(id: Long) = dao.incrementUseCount(id)

    suspend fun setLocked(id: Long, locked: Boolean) = dao.setLocked(id, locked)

    suspend fun pruneExpired(): Int = dao.pruneExpired(System.currentTimeMillis())

    suspend fun pruneExhausted(): Int = dao.pruneExhausted()

    suspend fun findDuplicatesOf(content: String): List<Clip> =
        dao.findDuplicatesOf(content).map(Clip.Companion::fromEntity)

    suspend fun mergeDuplicates(keepId: Long, duplicateIds: List<Long>) {
        if (duplicateIds.isEmpty()) return
        dao.deleteAllByIds(duplicateIds)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun pruneOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)
    suspend fun insertForUndo(entity: ClipEntity) = dao.restore(entity)
    suspend fun restoreBulk(entities: List<ClipEntity>) = dao.restoreAll(entities)
    suspend fun count(): Int = dao.count()
    suspend fun getAll(): List<Clip> = dao.getAll().map(Clip.Companion::fromEntity)
    suspend fun getAllEntities(): List<ClipEntity> = dao.getAll()
    suspend fun insertForImport(entity: ClipEntity) = dao.insert(entity)
    suspend fun countSince(since: Long): Int = dao.countSince(since)
    suspend fun countByType(): List<Pair<ClipType, Int>> =
        dao.countByType().mapNotNull { tc ->
            runCatching { ClipType.valueOf(tc.type) to tc.cnt }.getOrNull()
        }
    suspend fun totalContentBytes(): Long = dao.totalContentLength() ?: 0L
}