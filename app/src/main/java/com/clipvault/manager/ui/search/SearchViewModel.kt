package com.clipvault.manager.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.domain.model.Clip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Clip> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ClipboardRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val searchResults = query
        .debounce(150)
        .flatMapLatest { q ->
            if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.search(q)
        }

    val state: StateFlow<SearchUiState> = combine(query, searchResults) { q, results ->
        SearchUiState(query = q, results = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) { query.value = q }
}