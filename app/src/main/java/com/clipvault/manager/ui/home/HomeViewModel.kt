package com.clipvault.manager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.repository.UrlPreviewRepository
import com.clipvault.manager.domain.PasteQueueManager
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.service.ClipboardMonitorService
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Sentinel for "URL was fetched but yielded no title" — ConcurrentHashMap
 *  rejects null values, so negatives are cached as an empty string. */
private const val URL_NO_TITLE = ""

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
    val justCopiedForId: Long? = null,
    val savingNow: Boolean = false,
    val multiSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val monitoringActive: Boolean = true,
    val activeFilter: ClipType? = null,
    val favoritesOnly: Boolean = false,
    val queueItems: List<ClipEntity> = emptyList(),
    val queueIndex: Int = 0,
    val pinnedClips: List<Clip> = emptyList(),
    val totalCount: Int = 0,
    val loadedCount: Int = 0
)

private data class SelectionMeta(
    val justCopied: Long?,
    val saving: Boolean,
    val multiSelect: Boolean,
    val selected: Set<Long>,
    val monitoring: Boolean
)

private data class QueueAndPinned(
    val items: List<ClipEntity>,
    val index: Int,
    val pinned: List<Clip>,
    val total: Int
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val settings: SettingsManager,
    private val queueManager: PasteQueueManager,
    private val urlPreviewRepository: UrlPreviewRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private     val query = MutableStateFlow("")
    private val justCopied = MutableStateFlow<Long?>(null)
    private val saving = MutableStateFlow(false)
    private val multiSelectMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val activeFilter = MutableStateFlow<ClipType?>(null)
    private val favoritesOnly = MutableStateFlow(false)

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    // ── URL preview titles ───────────────────────────────────────────
    // SnapshotStateMap keyed by clip id: writes invalidate only the rows that
    // read their own key, so a title arriving mid-scroll doesn't recompose the
    // whole visible list (the old whole-map StateFlow replacement did).
    //
    // NOTE: ConcurrentHashMap forbids null values — putting one throws an
    // immediate NPE (crash log: HomeViewModel.kt fetchUrlTitle →
    // ConcurrentHashMap.put). Negative results ("fetched, no title") are
    // therefore cached as [URL_NO_TITLE] and converted back to null on read.
    private val titleCache = ConcurrentHashMap<String, String>()
    val titleMap = mutableStateMapOf<Long, String?>()

    // In-flight fetches shared across concurrent callers so N clips with the
    // same URL scrolling into view together coalesce into a single HTTP+DB pass
    // (the old containsKey-then-launch TOCTOU launched one coroutine per call).
    private val titleFetchInFlight = ConcurrentHashMap<String, Deferred<String?>>()

    /** Fetch a URL's page title in the background (cached in-memory + DB). */
    fun fetchUrlTitle(clipId: Long, url: String) {
        if (titleCache.containsKey(url)) {
            titleMap[clipId] = cacheGet(url)
            return
        }
        viewModelScope.launch {
            try {
                val deferred = titleFetchInFlight.getOrPut(url) {
                    async(Dispatchers.IO) {
                        urlPreviewRepository.getCached(url)
                            ?: urlPreviewRepository.refresh(url)
                    }
                }
                val title = deferred.await()
                titleCache[url] = title ?: URL_NO_TITLE
                titleMap[clipId] = title
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Preview is best-effort by contract — a failed fetch must
                // degrade to "no title", never take the process down.
                // Cached as a negative so an unreachable URL isn't refetched
                // (and re-failed) on every scroll pass this session.
                titleCache[url] = URL_NO_TITLE
            } finally {
                titleFetchInFlight.remove(url)
            }
        }
    }

    private fun cacheGet(url: String): String? =
        titleCache[url]?.takeIf { it != URL_NO_TITLE }

    init {
        // Rebind persisted queue entities after a restart: the manager restores
        // ids from DataStore but needs the clip rows to render the tray.
        viewModelScope.launch {
            queueManager.queueFlow.collect { data ->
                if (data.clipIds.isNotEmpty()) {
                    queueManager.bindClips(repository.getByIds(data.clipIds))
                }
            }
        }
    }

    /**
     * Paged stream of the history list. Built from a [combine] over the three
     * inputs the user can change (search query, type filter, favorites toggle)
     * so any of them invalidates the PagingSource and triggers a fresh window;
     * `cachedIn(viewModelScope)` keeps the loaded pages alive across config
     * changes. The clips themselves are NOT in [HomeUiState] — they're consumed
     * directly by the Composable via [collectAsLazyPagingItems].
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val clipsFlow: Flow<PagingData<Clip>> = combine(
        query.debounce(150),
        activeFilter,
        favoritesOnly
    ) { q, filter, fav ->
        Triple(q, filter, fav)
    }.flatMapLatest { (q, filter, fav) ->
        val source: Flow<PagingData<Clip>> = when {
            q.length >= 2 -> repository.pagingSearch(q)
            filter != null -> repository.pagingByType(filter)
            else -> repository.pagingAll()
        }
        source.map { paging -> if (fav) paging.filter { it.isFavorite } else paging }
    }.cachedIn(viewModelScope)

    private val loadedIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Tracks the ids the Composable has currently loaded from the paged stream
     *  so multi-select "Select all" / "X of Y" reflects what the user can see. */
    fun setLoadedIds(ids: Set<Long>) { loadedIds.value = ids }

    val state: StateFlow<HomeUiState> = combine(
        combine(
            justCopied,
            saving,
            multiSelectMode,
            selectedIds,
            settings.monitoringEnabled
        ) { copied, isSaving, isMulti, selected, monitoring ->
            SelectionMeta(copied, isSaving, isMulti, selected, monitoring)
        },
        activeFilter,
        favoritesOnly,
        combine(
            queueManager.items,
            queueManager.currentIndex,
            repository.observePinned(),
            repository.observeCount()
        ) { items, index, pinned, total ->
            QueueAndPinned(items, index, pinned, total)
        },
        loadedIds
    ) { meta, filter, favOnly, queueAndPinned, loaded ->
        HomeUiState(
            justCopiedForId = meta.justCopied,
            savingNow = meta.saving,
            multiSelectMode = meta.multiSelect,
            selectedIds = meta.selected,
            monitoringActive = meta.monitoring,
            activeFilter = filter,
            favoritesOnly = favOnly,
            queueItems = queueAndPinned.items,
            queueIndex = queueAndPinned.index,
            pinnedClips = queueAndPinned.pinned,
            totalCount = queueAndPinned.total,
            loadedCount = loaded.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) { query.value = q }

    fun setFilter(type: ClipType?) {
        activeFilter.value = type
    }

    fun setFavoritesOnly(v: Boolean) { favoritesOnly.value = v }

    fun toggleFavorite(clip: Clip) = viewModelScope.launch {
        repository.toggleFavorite(clip.id, !clip.isFavorite)
    }

    // ── Paste queue ──────────────────────────────────────────────────

    fun removeFromQueue(clipId: Long) = viewModelScope.launch {
        queueManager.removeFromQueue(clipId)
    }

    fun moveInQueue(clipId: Long, newIndex: Int) = viewModelScope.launch {
        queueManager.moveTo(clipId, newIndex)
    }

    fun advanceQueue() = viewModelScope.launch {
        queueManager.advance()
    }

    fun clearQueue() = viewModelScope.launch {
        queueManager.clear()
    }

    fun togglePin(clip: Clip) = viewModelScope.launch {
        val nowPinned = !clip.isPinned
        repository.togglePin(clip.id, nowPinned)
        _events.emit(HomeEvent.ToggledPin(nowPinned))
    }

    fun delete(clip: Clip) = viewModelScope.launch {
        val entity = toEntity(clip)
        repository.delete(clip.id)
        _events.emit(HomeEvent.Deleted(entity))
    }

    fun undoDelete(entity: ClipEntity) = viewModelScope.launch {
        repository.insertForUndo(entity)
    }

    fun flashCopied(id: Long) = viewModelScope.launch {
        justCopied.value = id
        _events.emit(HomeEvent.Copied(id))
        delay(1400)
        if (justCopied.value == id) justCopied.value = null
    }

    fun saveCurrentClipboard(content: String) = viewModelScope.launch {
        saving.value = true
        val id = repository.saveIfNew(content, sourceLabel = "fab")
        _events.emit(HomeEvent.SavedNew(id != null))
        delay(400)
        saving.value = false
    }

    /**
     * Read the system clipboard off the main thread, save it, and emit the usual
     * SavedNew event. Used by the Save FAB so the click handler never blocks
     * waiting on `ClipboardManager#primaryClip` / `coerceToText`.
     */
    suspend fun saveCurrentClipboardNow(silent: Boolean = false) {
        if (!silent) saving.value = true
        val content = withContext(Dispatchers.IO) {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            } catch (_: SecurityException) {
                ""
            }
        }
        // Android 10+ denies clipboard reads whenever the app isn't the
        // focused window (system log: "Denying clipboard access … not in
        // focus"), which silently broke background autosave. Reading here is
        // legal because we only run while RESUMED — so every time the user
        // opens/returns to the app, anything new on their clipboard lands in
        // history without them touching the FAB.
        val id = repository.saveIfNew(content, sourceLabel = if (silent) "resume" else "fab")
        if (!silent) {
            _events.emit(HomeEvent.SavedNew(id != null))
            delay(400)
            saving.value = false
        }
    }

    fun deleteAll() = viewModelScope.launch { repository.deleteAllUnpinned() }

    /** Count a clip as "used" (drives use-limit expiry). */
    fun recordUsage(clipId: Long) = viewModelScope.launch {
        repository.incrementUseCount(clipId)
    }

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
        selectedIds.value = loadedIds.value
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
        // Decide from the DB rows, not the filtered display list: selection
        // survives filter/search changes, and a stale `state.clips` could flip
        // the pin/unpin direction.
        val anyUnpinned = repository.getByIds(ids).any { !it.isPinned }
        repository.bulkSetPinned(ids, anyUnpinned)
        _events.emit(HomeEvent.BulkPinned(ids.size))
        exitMultiSelect()
    }

    fun bulkDelete() = viewModelScope.launch {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return@launch
        val entities = repository.getByIds(ids)
        repository.bulkDelete(ids)
        _events.emit(HomeEvent.BulkDeleted(entities))
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
        _events.emit(if (now) HomeEvent.MonitoringPaused else HomeEvent.MonitoringResumed)
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