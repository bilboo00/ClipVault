package com.clipvault.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clipvault.manager.data.local.entity.UrlPreviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlPreviewDao {
    @Query("SELECT * FROM url_previews WHERE url = :url LIMIT 1")
    suspend fun get(url: String): UrlPreviewEntity?

    @Query("SELECT * FROM url_previews WHERE url = :url")
    fun observeByUrl(url: String): Flow<UrlPreviewEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preview: UrlPreviewEntity)

    @Query("DELETE FROM url_previews WHERE fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}