package com.clipvault.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.clipvault.manager.data.local.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {

    // SQLite/Room doesn't support NULLS LAST — emulate with "IS NULL" sentinel
    // (NULLs get 1, non-NULLs get 0; ASC sort puts 0 first = non-nulls first)
    @Query("""
        SELECT * FROM snippets
        ORDER BY (lastUsedAt IS NULL), lastUsedAt DESC, createdAt DESC
    """)
    fun observeAll(): Flow<List<SnippetEntity>>

    @Query("SELECT * FROM snippets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SnippetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snippet: SnippetEntity): Long

    @Update
    suspend fun update(snippet: SnippetEntity)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE snippets SET useCount = useCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun recordUsage(id: Long, timestamp: Long)

    @Query("""
        SELECT * FROM snippets
        WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'
        ORDER BY (lastUsedAt IS NULL), lastUsedAt DESC, createdAt DESC
    """)
    fun search(query: String): Flow<List<SnippetEntity>>
}