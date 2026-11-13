package com.clipvault.manager.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.domain.model.Clip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchScope { ALL, PINNED, FAVORITES }

data class SearchUiState(
    val query: String = "",
    val results: List<Clip> = emptyList(),
    val justCopiedId: Long? = null,
    val scope: SearchScope = SearchScope.ALL,
    val typeFilter: ClipType? = null,
    val recentSearches: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val settings: SettingsManager
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val copiedId = MutableStateFlow<Long?>(null)
    private val scope = MutableStateFlow(SearchScope.ALL)
    private val typeFilter = MutableStateFlow<ClipType?>(null)

    private val searchResults = query
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.searchFts(q)
        }

    // Scope/type filters apply in memory over the FTS result window: search
    // hits are already realized as a list, so this is a cheap pass and it
    // keeps the FTS query path (with its escaping rules) untouched.
    // Five flows max per combine — the typed overloads stop at five.
    val state: StateFlow<SearchUiState> = combine(
        query,
        searchResults,
        copiedId,
        combine(scope, typeFilter) { s, t -> s to t },
        settings.recentSearches
    ) { q: String, results: List<Clip>, copied: Long?, filters: Pair<SearchScope, ClipType?>, recents: List<String> ->
        val (s, t) = filters
        SearchUiState(
            query = q,
            results = results.filter { clip ->
                (s == SearchScope.ALL ||
                    (s == SearchScope.PINNED && clip.isPinned) ||
                    (s == SearchScope.FAVORITES && clip.isFavorite)) &&
                    (t == null || clip.type == t)
            },
            justCopiedId = copied,
            scope = s,
            typeFilter = t,
            recentSearches = recents
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) { query.value = q }

    fun setScope(s: SearchScope) { scope.value = s }

    fun setTypeFilter(type: ClipType?) { typeFilter.value = type }

    fun submitSearch() = viewModelScope.launch {
        try {
            settings.addRecentSearch(query.value)
        } catch (_: Exception) {
            // Best-effort — a failed recents write must never break search
        }
    }

    fun clearRecentSearches() = viewModelScope.launch {
        try {
            settings.clearRecentSearches()
        } catch (_: Exception) { }
    }

    fun flashCopied(id: Long) = viewModelScope.launch {
        copiedId.value = id
        delay(1_500)
        if (copiedId.value == id) copiedId.value = null
    }

    fun recordUsage(clipId: Long) = viewModelScope.launch {
        repository.incrementUseCount(clipId)
    }
}
