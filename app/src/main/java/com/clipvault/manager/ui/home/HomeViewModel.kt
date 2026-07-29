package com.clipvault.manager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.service.ClipboardMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeEvent {
    data class Copied(val clipId: Long) : HomeEvent
    data class Deleted(val clip: ClipEntity) : HomeEvent
    data class BulkDeleted(val clips: List<ClipEntity>) : HomeEvent
    data class SavedNew(val success: Boolean) : HomeEvent
    data class ToggledPin(val nowPinned: Boolean) : HomeEvent
    data class BulkPinned(val count: Int) : HomeEvent
    data object MonitoringPaused : HomeEvent
    data object MonitoringResumed : HomeEvent
}

data class HomeUiState(
    val clips: List<Clip> = emptyList(),
    val justCopiedForId: Long? = null,
    val savingNow: Boolean = false,
    val multiSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val monitoringActive: Boolean = true,
    val activeFilter: ClipType? = null
)

private data class SelectionMeta(
    val justCopied: Long?,
    val saving: Boolean,
    val multiSelect: Boolean,
    val selected: Set<Long>,
    val monitoring: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val settings: SettingsManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private     val query = MutableStateFlow("")
    private val justCopied = MutableStateFlow<Long?>(null)
    private val saving = MutableStateFlow(false)
    private val multiSelectMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val activeFilter = MutableStateFlow<ClipType?>(null)

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

     val state: StateFlow<HomeUiState> = combine(
        query.debounce(150).flatMapLatest { q ->
            if (q.length >= 2) repository.search(q)
            else activeFilter.flatMapLatest { filter ->
                if (filter != null) repository.observeByType(filter)
                else repository.observeAll()
            }
        },
        combine(
            justCopied,
            saving,
            multiSelectMode,
            selectedIds,
            settings.monitoringEnabled
        ) { copied, isSaving, isMulti, selected, monitoring ->
            SelectionMeta(copied, isSaving, isMulti, selected, monitoring)
        },
        activeFilter
    ) { clips, meta, filter ->
        HomeUiState(
            clips = clips,
            justCopiedForId = meta.justCopied,
            savingNow = meta.saving,
            multiSelectMode = meta.multiSelect,
            selectedIds = meta.selected,
            monitoringActive = meta.monitoring,
            activeFilter = filter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) { query.value = q }

    fun setFilter(type: ClipType?) {
        activeFilter.value = type
    }

    fun togglePin(clip: Clip) = viewModelScope.launch {
        val nowPinned = !clip.isPinned
        repository.togglePin(clip.id, nowPinned)
        _events.send(HomeEvent.ToggledPin(nowPinned))
    }

    fun delete(clip: Clip) = viewModelScope.launch {
        val entity = toEntity(clip)
        repository.delete(clip.id)
        _events.send(HomeEvent.Deleted(entity))
    }

    fun undoDelete(entity: ClipEntity) = viewModelScope.launch {
        repository.insertForUndo(entity)
    }

    fun flashCopied(id: Long) = viewModelScope.launch {
        justCopied.value = id
        _events.send(HomeEvent.Copied(id))
        delay(1400)
        if (justCopied.value == id) justCopied.value = null
    }

    fun saveCurrentClipboard(content: String) = viewModelScope.launch {
        saving.value = true
        val id = repository.saveIfNew(content, sourceLabel = "fab")
        _events.send(HomeEvent.SavedNew(id != null))
        delay(400)
        saving.value = false
    }

    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }

    // ── Multi-select ─────────────────────────────────────────────────

    /** Long-press on a card → enter selection mode with that card pre-selected. */
    fun enterMultiSelect(initialId: Long) {
        if (!multiSelectMode.value) multiSelectMode.value = true
        selectedIds.value = selectedIds.value + initialId
    }

    /** Tap on a card while in selection mode → toggle its selection. */
    fun toggleSelection(id: Long) {
        val current = selectedIds.value
        selectedIds.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        selectedIds.value = state.value.clips.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun exitMultiSelect() {
        multiSelectMode.value = false
        selectedIds.value = emptySet()
    }

    fun bulkPin() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        // Pin if any selected clip is currently unpinned; otherwise unpin all
        val anyUnpinned = state.value.clips
            .filter { it.id in selectedIds.value }
            .any { !it.isPinned }
        repository.bulkSetPinned(ids, anyUnpinned)
        _events.send(HomeEvent.BulkPinned(ids.size))
        exitMultiSelect()
    }

    fun bulkDelete() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        val entities = repository.getByIds(ids)
        repository.bulkDelete(ids)
        _events.send(HomeEvent.BulkDeleted(entities))
        exitMultiSelect()
    }

    /**
     * Persist a new order for pinned items after the user drag-reorders them.
     * `orderedIds` is the IDs of pinned clips in their new top-to-bottom order.
     */
    fun persistPinnedOrder(orderedIds: List<Long>) = viewModelScope.launch {
        if (orderedIds.isEmpty()) return@launch
        repository.reorderPinned(orderedIds)
    }

    fun undoBulkDelete(entities: List<ClipEntity>) = viewModelScope.launch {
        repository.restoreBulk(entities)
    }

    // ── Kill switch ──────────────────────────────────────────────────

    fun toggleMonitoring() = viewModelScope.launch {
        val now = state.value.monitoringActive
        settings.setMonitoring(!now)
        if (!now) ClipboardMonitorService.start(context)
        else ClipboardMonitorService.stop(context)
        _events.send(if (now) HomeEvent.MonitoringPaused else HomeEvent.MonitoringResumed)
    }

    private fun toEntity(clip: Clip) = ClipEntity(
        id = clip.id,
        content = clip.content,
        type = clip.type,
        isPinned = clip.isPinned,
        isFavorite = clip.isFavorite,
        createdAt = clip.createdAt,
        sourceLabel = clip.sourceLabel
    )
}