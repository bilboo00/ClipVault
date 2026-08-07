package com.clipvault.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.clipvault.manager.data.local.entity.ClipTagCrossRef
import com.clipvault.manager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM clip_tags WHERE clipId = :clipId")
    suspend fun getCrossRefsForClip(clipId: Long): List<ClipTagCrossRef>

    @Query("SELECT * FROM clip_tags WHERE clipId = :clipId")
    fun observeCrossRefsForClip(clipId: Long): Flow<List<ClipTagCrossRef>>

    @Query("SELECT * FROM clip_tags WHERE tagId = :tagId")
    suspend fun getCrossRefsForTag(tagId: Long): List<ClipTagCrossRef>

    @Query("SELECT tagId, COUNT(*) AS count FROM clip_tags GROUP BY tagId")
    fun observeUsageCounts(): Flow<List<TagUsageCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: ClipTagCrossRef)

    @Query("DELETE FROM clip_tags WHERE clipId = :clipId AND tagId = :tagId")
    suspend fun removeCrossRef(clipId: Long, tagId: Long)

    @Transaction
    suspend fun setTagsForClip(clipId: Long, tagIds: List<Long>) {
        getCrossRefsForClip(clipId).forEach { ref ->
            if (ref.tagId !in tagIds) removeCrossRef(clipId, ref.tagId)
        }
        tagIds.forEach { tagId ->
            insertCrossRef(ClipTagCrossRef(clipId, tagId))
        }
    }
}

data class TagUsageCount(val tagId: Long, val count: Int)