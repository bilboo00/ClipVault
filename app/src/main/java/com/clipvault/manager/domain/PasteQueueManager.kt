package com.clipvault.manager.domain

import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.preferences.PasteQueueData
import com.clipvault.manager.data.preferences.PasteQueueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasteQueueManager @Inject constructor(
    private val storage: PasteQueueStorage
) {
    private val _clipIds = MutableStateFlow<List<Long>>(emptyList())
    private val _currentIndex = MutableStateFlow(0)
    private val _items = MutableStateFlow<List<ClipEntity>>(emptyList())

    val items: StateFlow<List<ClipEntity>> = _items.asStateFlow()
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    val queueFlow: Flow<PasteQueueData> = storage.queue

    suspend fun hydrateFromStorage() {
        storage.queue.collect { data ->
            _clipIds.value = data.clipIds
            _currentIndex.value = data.currentIndex.coerceIn(
                0, data.clipIds.size.coerceAtLeast(0)
            )
        }
    }

    fun bindClips(clips: List<ClipEntity>) {
        _items.value = clips.filter { it.id in _clipIds.value }
    }

    suspend fun addToQueue(clip: ClipEntity) {
        if (_clipIds.value.contains(clip.id)) return
        _clipIds.value = _clipIds.value + clip.id
        _items.value = _items.value + clip
        persist()
    }

    suspend fun removeFromQueue(clipId: Long) {
        _clipIds.value = _clipIds.value.filter { it != clipId }
        _items.value = _items.value.filter { it.id != clipId }
        if (_currentIndex.value >= _clipIds.value.size) _currentIndex.value = 0
        persist()
    }

    suspend fun moveTo(clipId: Long, newIndex: Int) {
        val current = _items.value.toMutableList()
        val fromIndex = current.indexOfFirst { it.id == clipId }
        if (fromIndex < 0) return
        val item = current.removeAt(fromIndex)
        val safeIndex = newIndex.coerceIn(0, current.size)
        current.add(safeIndex, item)
        _items.value = current
        _clipIds.value = current.map { it.id }
        persist()
    }

    suspend fun advance() {
        if (_clipIds.value.isEmpty()) return
        _currentIndex.value = (_currentIndex.value + 1) % _clipIds.value.size
        persist()
    }

    suspend fun clear() {
        _clipIds.value = emptyList()
        _currentIndex.value = 0
        _items.value = emptyList()
        persist()
    }

    private suspend fun persist() {
        storage.setQueue(_clipIds.value, _currentIndex.value)
    }
}