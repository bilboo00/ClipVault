package com.clipvault.manager.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipFtsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Query("SELECT * FROM clips ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    fun pagingSourceAll(): PagingSource<Int, ClipEntity>

    @Query("SELECT * FROM clips ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE type = :type ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    fun observeByType(type: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE type = :type ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC")
    fun pagingSourceByType(type: String): PagingSource<Int, ClipEntity>

    @Query("UPDATE clips SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String?)

    @Query("UPDATE clips SET expiresAt = :expiresAt WHERE id = :id")
    suspend fun setExpiresAt(id: Long, expiresAt: Long?)

    @Query("UPDATE clips SET useLimit = :useLimit, useCount = 0 WHERE id = :id")
    suspend fun setUseLimit(id: Long, useLimit: Int?)

    @Query("UPDATE clips SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Long)

    @Query("UPDATE clips SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("DELETE FROM clips WHERE expiresAt IS NOT NULL AND expiresAt < :now AND isPinned = 0")
    suspend fun pruneExpired(now: Long): Int

    @Query("DELETE FROM clips WHERE useLimit IS NOT NULL AND useCount >= useLimit AND isPinned = 0")
    suspend fun pruneExhausted(): Int

    @Query("SELECT COUNT(*) FROM clips WHERE content = :content")
    suspend fun countAllByContent(content: String): Int

    @Query("SELECT * FROM clips WHERE content = :content ORDER BY createdAt DESC")
    suspend fun findDuplicatesOf(content: String): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE id != :keepId AND content = :content")
    suspend fun findDuplicatesExcluding(keepId: Long, content: String): List<ClipEntity>

    @Query(
        "SELECT content, COUNT(*) AS count, " +
            "COALESCE(" +
            "  (SELECT c2.id FROM clips c2 WHERE c2.content = c1.content AND c2.isPinned = 1 " +
            "   ORDER BY c2.createdAt DESC LIMIT 1), " +
            "  (SELECT c3.id FROM clips c3 WHERE c3.content = c1.content " +
            "   ORDER BY c3.createdAt DESC LIMIT 1)" +
            ") AS keepId " +
            "FROM clips c1 GROUP BY c1.content HAVING COUNT(*) > 1 ORDER BY COUNT(*) DESC"
    )
    suspend fun findDuplicateGroups(): List<DuplicateGroup>

    @Query("UPDATE clips SET useCount = useCount + COALESCE((SELECT SUM(c2.useCount) FROM clips c2 WHERE c2.id IN (:ids)), 0) WHERE id = :keepId")
    suspend fun foldUseCount(keepId: Long, ids: List<Long>)

    @Query("INSERT OR IGNORE INTO clip_tags(clipId, tagId, addedAt) SELECT :keepId, tagId, MIN(addedAt) FROM clip_tags WHERE clipId IN (:ids) GROUP BY tagId")
    suspend fun moveTagsTo(keepId: Long, ids: List<Long>)

    @Query("INSERT OR IGNORE INTO clip_collections(clipId, collectionId, addedAt) SELECT :keepId, collectionId, MIN(addedAt) FROM clip_collections WHERE clipId IN (:ids) GROUP BY collectionId")
    suspend fun moveCollectionsTo(keepId: Long, ids: List<Long>)

    @Query("DELETE FROM clips WHERE id IN (:ids)")
    suspend fun deleteAllByIds(ids: List<Long>)

    /**
     * Merge all duplicate rows of a group into [keepId] atomically: use counts
     * are folded, tag/collection crossrefs are re-pointed, notes are kept if
     * the keeper has none, and the duplicates are deleted — all in one
     * transaction so a crash mid-merge can't leave the history half-migrated.
     */
    @Transaction
    suspend fun mergeDuplicateGroup(keepId: Long, duplicateIds: List<Long>, notesToKeep: String?) {
        foldUseCount(keepId, duplicateIds)
        moveTagsTo(keepId, duplicateIds)
        moveCollectionsTo(keepId, duplicateIds)
        if (!notesToKeep.isNullOrBlank()) setNotes(keepId, notesToKeep)
        deleteAllByIds(duplicateIds)
    }

    @Query("SELECT * FROM clips WHERE content LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC LIMIT 100")
    fun search(query: String): Flow<List<ClipEntity>>

    @RawQuery(observedEntities = [ClipEntity::class, ClipFtsEntity::class])
    fun searchFts(query: SupportSQLiteQuery): Flow<List<ClipEntity>>

    @Query("SELECT clips.* FROM clips JOIN clips_fts ON clips_fts.rowid = clips.id WHERE clips_fts MATCH :ftsQuery ORDER BY isPinned DESC, sortOrder ASC, createdAt DESC LIMIT 100")
    fun pagingSourceSearchFts(ftsQuery: String): PagingSource<Int, ClipEntity>

    @Query("UPDATE clips SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("UPDATE clips SET sortOrder = :order WHERE id IN (:ids)")
    suspend fun setSortOrderBulk(ids: List<Long>, order: Int)

    @Transaction
    suspend fun reorderPinnedWithOrders(orders: Map<Long, Int>) {
        orders.forEach { (id, order) -> setSortOrder(id, order) }
    }

    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM clips WHERE isPinned = 1")
    suspend fun minPinnedSortOrder(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM clips WHERE isPinned = 1")
    suspend fun maxPinnedSortOrder(): Int

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClipEntity?

    @Query("SELECT * FROM clips WHERE id = :id")
    fun observeById(id: Long): Flow<ClipEntity?>

    @Query("SELECT content FROM clips ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestContent(): String?

    @Query("SELECT COUNT(*) FROM clips WHERE content = :content LIMIT 1")
    suspend fun countByContent(content: String): Int

    @Query("SELECT COUNT(*) FROM clips WHERE imageUri = :path LIMIT 1")
    suspend fun countByImageUri(path: String): Int

    @Query("SELECT imageUri FROM clips WHERE imageUri IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Query("SELECT imageUri FROM clips WHERE imageUri IS NOT NULL AND isPinned = 1")
    suspend fun getPinnedImagePaths(): List<String>

    @Query("UPDATE clips SET imageUri = :newPath WHERE imageUri = :oldPath")
    suspend fun updateImagePath(oldPath: String, newPath: String)

    @Query("UPDATE clips SET imageUri = NULL WHERE imageUri = :path")
    suspend fun clearImageUri(path: String): Int

    @Query("DELETE FROM clip_tags WHERE clipId NOT IN (SELECT id FROM clips)")
    suspend fun purgeOrphanedTagRefs(): Int

    @Query("DELETE FROM clip_tags WHERE tagId NOT IN (SELECT id FROM tags)")
    suspend fun purgeOrphanedTagOwners(): Int

    @Query("DELETE FROM clip_collections WHERE clipId NOT IN (SELECT id FROM clips)")
    suspend fun purgeOrphanedCollectionRefs(): Int

    @Query("DELETE FROM clip_collections WHERE collectionId NOT IN (SELECT id FROM collections)")
    suspend fun purgeOrphanedCollectionOwners(): Int

    @Transaction
    suspend fun purgeOrphanedCrossRefs() {
        purgeOrphanedTagRefs()
        purgeOrphanedTagOwners()
        purgeOrphanedCollectionRefs()
        purgeOrphanedCollectionOwners()
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(clip: ClipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(clip: ClipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(clips: List<ClipEntity>): List<Long>

    @Update
    suspend fun update(clip: ClipEntity)

    @Query("UPDATE clips SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE clips SET isPinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinnedBulk(ids: List<Long>, pinned: Boolean): Int

    @Query("UPDATE clips SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clips WHERE isPinned = 0")
    suspend fun deleteUnpinned(): Int

    @Query("DELETE FROM clips WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("SELECT * FROM clips WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE isPinned = 0")
    suspend fun getUnpinned(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE isPinned = 1 ORDER BY sortOrder ASC, createdAt DESC")
    fun observePinned(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE expiresAt IS NOT NULL AND expiresAt < :now AND isPinned = 0")
    suspend fun getExpiredBefore(now: Long): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE useLimit IS NOT NULL AND useCount >= useLimit AND isPinned = 0")
    suspend fun getExhausted(): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE isPinned = 0 AND createdAt < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<ClipEntity>

    @Query("DELETE FROM clips")
    suspend fun deleteAll()

    @Query("DELETE FROM clips WHERE id IN (SELECT id FROM clips WHERE isPinned = 0 AND createdAt < :cutoff LIMIT 500)")
    suspend fun deleteOlderThanBatch(cutoff: Long): Int

    /**
     * Batch the retention prune in 500-row chunks so a single statement
     * can't hold the DB write-lock long enough to stall foreground
     * writes; loop until the DELETE affects zero rows.
     */
    @Transaction
    suspend fun deleteOlderThan(cutoff: Long): Int {
        var total = 0
        while (true) {
            val deleted = deleteOlderThanBatch(cutoff)
            total += deleted
            if (deleted == 0) break
        }
        return total
    }

    @Query("SELECT COUNT(*) FROM clips")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM clips WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT type, COUNT(*) as cnt FROM clips GROUP BY type ORDER BY cnt DESC")
    suspend fun countByType(): List<TypeCount>

    @Query("SELECT SUM(LENGTH(content)) FROM clips")
    suspend fun totalContentLength(): Long?

    @Query("SELECT COUNT(*) FROM clips")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clips WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT type, COUNT(*) as cnt FROM clips GROUP BY type ORDER BY cnt DESC")
    fun observeCountByType(): Flow<List<TypeCount>>

    @Query("SELECT SUM(LENGTH(content)) FROM clips")
    fun observeTotalContentLength(): Flow<Long?>
}

data class TypeCount(val type: String, val cnt: Int)

data class DuplicateGroup(val content: String, val count: Int, val keepId: Long)