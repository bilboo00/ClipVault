package com.clipvault.manager.data.repository

import com.clipvault.manager.data.local.dao.SnippetDao
import com.clipvault.manager.data.local.entity.SnippetEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepository @Inject constructor(
    private val dao: SnippetDao
) {
    fun observeAll(): Flow<List<SnippetEntity>> = dao.observeAll()
    fun search(query: String): Flow<List<SnippetEntity>> = dao.search(query)

    suspend fun get(id: Long): SnippetEntity? = dao.getById(id)
    suspend fun create(title: String, content: String): Long =
        dao.insert(SnippetEntity(title = title, content = content))

    suspend fun update(snippet: SnippetEntity) = dao.update(snippet)
    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun recordUsage(id: Long) {
        dao.recordUsage(id, System.currentTimeMillis())
    }
}