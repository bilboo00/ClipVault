package com.clipvault.manager.domain

import android.util.Log
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.preferences.PasteQueueData
import com.clipvault.manager.data.preferences.PasteQueueStorage
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasteQueueManager @Inject constructor(
    private val storage: PasteQueueStorage
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.w("PasteQueueManager", "scope failure", e)
        }
    )
    private val _clipIds = MutableStateFlow<List<Long>>(emptyList())
    private val _currentIndex = MutableStateFlow(0)
    private val _items = MutableStateFlow<List<ClipEntity>>(emptyList())

    val items: StateFlow<List<ClipEntity>> = _items.asStateFlow()
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    val queueFlow: StateFlow<PasteQueueData> = storage.queue

    init {
        // Restore the persisted queue on process start; without this the
        // in-memory state was always empty after a restart.
        scope.launch { hydrateFromStorage() }
    }

    private suspend fun hydrateFromStorage() {
        storage.queue.collect { data ->
            _clipIds.value = data.clipIds
            val size = data.clipIds.size
            _currentIndex.value = if (size == 0) 0 else data.currentIndex.coerceIn(0, size - 1)
        }
    }

    fun bindClips(clips: List<ClipEntity>) {
        // Match rows back to the persisted queue order — `clips` (DB IN-clause)
        // order is arbitrary and would silently reorder the tray after restart.
        val byId = clips.associateBy { it.id }
        _items.value = _clipIds.value.mapNotNull { byId[it] }
    }

    suspend fun addToQueue(clip: ClipEntity) {
        if (_clipIds.value.contains(clip.id)) return
        _clipIds.value = _clipIds.value + clip.id
        _items.value = _items.value + clip
        persist()
    }

    suspend fun removeFromQueue(clipId: Long) {
        val removedIndex = _clipIds.value.indexOf(clipId)
        _clipIds.value = _clipIds.value.filter { it != clipId }
        _items.value = _items.value.filter { it.id != clipId }
        val size = _clipIds.value.size
        _currentIndex.value = when {
            size == 0 -> 0
            removedIndex < 0 -> _currentIndex.value.coerceAtMost(size - 1)
            removedIndex < _currentIndex.value -> _currentIndex.value - 1
            else -> _currentIndex.value.coerceAtMost(size - 1)
        }
        persist()
    }

    suspend fun moveTo(clipId: Long, newIndex: Int) {
        val current = _items.value.toMutableList()
        val fromIndex = current.indexOfFirst { it.id == clipId }
        if (fromIndex < 0) return
        // Keep "current" pointing at the same item after the reorder.
        val currentId = _items.value.getOrNull(_currentIndex.value)?.id
        val item = current.removeAt(fromIndex)
        val safeIndex = newIndex.coerceIn(0, current.size)
        current.add(safeIndex, item)
        _items.value = current
        _clipIds.value = current.map { it.id }
        _currentIndex.value = currentId
            ?.let { id -> current.indexOfFirst { it.id == id }.coerceAtLeast(0) }
            ?: 0
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
        storage.setQueue(PasteQueueData(_clipIds.value, _currentIndex.value))
    }
}