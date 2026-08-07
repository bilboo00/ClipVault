package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.dao.DuplicateGroup
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.domain.model.ClipClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery
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
        dao.search(query.escapeLikeQuery()).map { list -> list.map(Clip.Companion::fromEntity) }

    /**
     * Full-text search over the FTS4 index. The query is escaped into a quoted
     * prefix-phrase expression so user input never reaches FTS syntax parser
     * unguarded; malformed-input exceptions are swallowed as empty results.
     */
    fun searchFts(raw: String): Flow<List<Clip>> {
        val tokens = raw.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace("\"", "\"\"") }
        if (tokens.isEmpty()) return emptyFlow()
        val ftsQuery = tokens.joinToString(" ") { "\"$it\"*" }
        val sql = SimpleSQLiteQuery(
            "SELECT clips.* FROM clips " +
                "JOIN clips_fts ON clips_fts.rowid = clips.id " +
                "WHERE clips_fts MATCH ? " +
                "ORDER BY clips.isPinned DESC, clips.createdAt DESC LIMIT 100",
            arrayOf(ftsQuery)
        )
        return flow {
            emitAll(
                dao.searchFts(sql).map { list -> list.map(Clip.Companion::fromEntity) }
            )
        }.catch { emit(emptyList()) }
    }

    fun observeById(id: Long): Flow<Clip?> =
        dao.observeById(id).map { entity -> entity?.let(Clip.Companion::fromEntity) }

    suspend fun saveIfNew(content: String, sourceLabel: String? = null): Long? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        if (dao.countByContent(trimmed) > 0) return null
        val type = ClipClassifier.classify(trimmed)
        val entity = ClipEntity(
            content = trimmed,
            type = type,
            sourceLabel = sourceLabel,
            // One-time codes self-destruct after a short window so a leaked
            // verification code doesn't linger in history.
            expiresAt = if (type == ClipType.OTP)
                System.currentTimeMillis() + OTP_TTL_MS
            else null
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
        val updated = dao.setPinnedBulk(ids, pinned)
        if (pinned) {
            // Give each newly pinned clip a distinct sortOrder so the batch keeps
            // a stable order instead of all sharing one identical value.
            var next = dao.minPinnedSortOrder() - 1
            ids.forEach { id -> dao.setSortOrder(id, next--) }
        } else {
            dao.setSortOrderBulk(ids, 0)
        }
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

    suspend fun findDuplicateGroups(): List<DuplicateGroup> = dao.findDuplicateGroups()

    /**
     * Merge all duplicates of [content] into [keepId]: use counts are folded,
     * tag/collection crossrefs are re-pointed (skipping ones the keeper already
     * has), notes are preserved if the keeper has none, and the duplicate rows
     * are deleted. Pinned duplicates are never deleted.
     *
     * @return the number of clips deleted.
     */
    suspend fun mergeDuplicateGroup(keepId: Long, content: String): Int {
        val duplicates = dao.findDuplicatesExcluding(keepId, content).filter { !it.isPinned }
        if (duplicates.isEmpty()) return 0
        val ids = duplicates.map { it.id }
        dao.foldUseCount(keepId, ids)
        dao.moveTagsTo(keepId, ids)
        dao.moveCollectionsTo(keepId, ids)
        val keeper = dao.getById(keepId)
        if (keeper?.notes.isNullOrBlank()) {
            duplicates.firstOrNull { !it.notes.isNullOrBlank() }?.let {
                dao.setNotes(keepId, it.notes)
            }
        }
        dao.deleteAllByIds(ids)
        return ids.size
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun deleteAllUnpinned(): Int = dao.deleteUnpinned()
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

    /** Escape SQL LIKE wildcards so user queries match literally. */
    private fun String.escapeLikeQuery(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        /** One-time codes are removed 10 minutes after capture. */
        const val OTP_TTL_MS = 10 * 60 * 1000L
    }
}