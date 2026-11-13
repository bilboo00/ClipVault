package com.clipvault.manager.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.domain.model.Clip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which slice of the saved clips the list shows. */
enum class SavedSegment { ALL, PINNED, LIKED }

data class FavoritesUiState(
    val clips: List<Clip> = emptyList(),
    val totalSaved: Int = 0,
    val segment: SavedSegment = SavedSegment.ALL,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val justCopiedId: Long? = null
)

sealed interface FavoritesEvent {
    data class Deleted(val clip: ClipEntity) : FavoritesEvent
    data class BulkDeleted(val clips: List<ClipEntity>) : FavoritesEvent
    data class BulkPinned(val count: Int, val nowPinned: Boolean) : FavoritesEvent
    data class ToggledPin(val nowPinned: Boolean) : FavoritesEvent
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: ClipboardRepository
) : ViewModel() {

    private val segment = MutableStateFlow(SavedSegment.ALL)
    private val selectionMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val justCopied = MutableStateFlow<Long?>(null)

    private val _events = MutableSharedFlow<FavoritesEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<FavoritesEvent> = _events.asSharedFlow()

    val uiState: StateFlow<FavoritesUiState> = combine(
        repository.observeSaved(),
        segment,
        selectionMode,
        selectedIds,
        justCopied
    ) { saved, seg, selMode, ids, copied ->
        val filtered = when (seg) {
            SavedSegment.ALL -> saved
            SavedSegment.PINNED -> saved.filter { it.isPinned }
            SavedSegment.LIKED -> saved.filter { it.isFavorite }
        }
        // Drop selections whose clip was deleted / un-saved while selected.
        val visible = saved.mapTo(HashSet()) { it.id }
        FavoritesUiState(
            clips = filtered,
            totalSaved = saved.size,
            segment = seg,
            selectionMode = selMode,
            selectedIds = ids intersect visible,
            justCopiedId = copied
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun setSegment(value: SavedSegment) {
        segment.value = value
    }

    // ── Selection ────────────────────────────────────────────────────

    fun enterSelectionMode() {
        selectionMode.value = true
        selectedIds.value = emptySet()
    }

    /** Long-press on a card → enter selection mode with that card pre-selected. */
    fun enterSelectionMode(initialId: Long) {
        if (!selectionMode.value) selectionMode.value = true
        selectedIds.value = selectedIds.value + initialId
    }

    fun toggleSelection(id: Long) {
        val current = selectedIds.value
        selectedIds.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        selectedIds.value = uiState.value.clips.mapTo(HashSet()) { it.id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun exitSelectionMode() {
        selectionMode.value = false
        selectedIds.value = emptySet()
    }

    // ── Single-clip actions ──────────────────────────────────────────

    fun togglePin(clip: Clip) = viewModelScope.launch {
        val nowPinned = !clip.isPinned
        repository.togglePin(clip.id, nowPinned)
        _events.emit(FavoritesEvent.ToggledPin(nowPinned))
    }

    fun toggleFavorite(clip: Clip) = viewModelScope.launch {
        repository.toggleFavorite(clip.id, !clip.isFavorite)
    }

    fun delete(clip: Clip) = viewModelScope.launch {
        val entity = repository.getByIds(listOf(clip.id)).firstOrNull() ?: return@launch
        repository.delete(clip.id)
        _events.emit(FavoritesEvent.Deleted(entity))
    }

    fun undoDelete(entity: ClipEntity) = viewModelScope.launch {
        repository.insertForUndo(entity)
    }

    // ── Batch actions ────────────────────────────────────────────────

    /**
     * Pins the selection — or unpins it when every selected clip is already
     * pinned, so the same button works as a pin/unpin toggle.
     */
    fun bulkTogglePin() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        val anyUnpinned = repository.getByIds(ids).any { !it.isPinned }
        repository.bulkSetPinned(ids, anyUnpinned)
        _events.emit(FavoritesEvent.BulkPinned(ids.size, anyUnpinned))
        exitSelectionMode()
    }

    fun bulkDelete() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        val entities = repository.getByIds(ids)
        repository.bulkDelete(ids)
        _events.emit(FavoritesEvent.BulkDeleted(entities))
        exitSelectionMode()
    }

    fun undoBulkDelete(entities: List<ClipEntity>) = viewModelScope.launch {
        repository.restoreBulk(entities)
    }

    // ── Copy feedback ────────────────────────────────────────────────

    fun flashCopied(id: Long) = viewModelScope.launch {
        justCopied.value = id
        delay(1400)
        if (justCopied.value == id) justCopied.value = null
    }

    /** Count a clip as "used" (drives use-limit expiry). */
    fun recordUsage(clipId: Long) = viewModelScope.launch {
        repository.incrementUseCount(clipId)
    }
}
