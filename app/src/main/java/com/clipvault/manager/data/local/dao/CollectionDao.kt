package com.clipvault.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.clipvault.manager.data.local.entity.ClipCollectionCrossRef
import com.clipvault.manager.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY name ASC")
    suspend fun getAll(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Update
    suspend fun update(collection: CollectionEntity)

    @Delete
    suspend fun delete(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM clip_collections WHERE clipId = :clipId")
    suspend fun getCrossRefsForClip(clipId: Long): List<ClipCollectionCrossRef>

    @Query("SELECT * FROM clip_collections WHERE clipId = :clipId")
    fun observeCrossRefsForClip(clipId: Long): Flow<List<ClipCollectionCrossRef>>

    @Query("SELECT * FROM clip_collections WHERE collectionId = :collectionId")
    suspend fun getCrossRefsForCollection(collectionId: Long): List<ClipCollectionCrossRef>

    @Query("SELECT collectionId, COUNT(*) AS count FROM clip_collections GROUP BY collectionId")
    fun observeUsageCounts(): Flow<List<CollectionUsageCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: ClipCollectionCrossRef)

    @Query("DELETE FROM clip_collections WHERE clipId = :clipId AND collectionId = :collectionId")
    suspend fun removeCrossRef(clipId: Long, collectionId: Long)

    @Transaction
    suspend fun setCollectionsForClip(clipId: Long, collectionIds: List<Long>) {
        getCrossRefsForClip(clipId).forEach { ref ->
            if (ref.collectionId !in collectionIds) removeCrossRef(clipId, ref.collectionId)
        }
        collectionIds.forEach { collectionId ->
            insertCrossRef(ClipCollectionCrossRef(clipId, collectionId))
        }
    }
}

data class CollectionUsageCount(val collectionId: Long, val count: Int)