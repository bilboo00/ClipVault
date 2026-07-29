package com.clipvault.manager.ui.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.SnippetEntity
import com.clipvault.manager.data.repository.SnippetRepository
import com.clipvault.manager.domain.model.SnippetExpander
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetsUiState(
    val snippets: List<SnippetEntity> = emptyList(),
    val query: String = "",
    val editing: SnippetEntity? = null,
    val showEditor: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SnippetsViewModel @Inject constructor(
    private val repository: SnippetRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val editorState = MutableStateFlow<Pair<SnippetEntity?, Boolean>>(null to false)

    val state: StateFlow<SnippetsUiState> = combine(
        query.flatMapLatest { q ->
            if (q.isBlank()) repository.observeAll() else repository.search(q)
        },
        query,
        editorState
    ) { snippets: List<SnippetEntity>, q: String, editor: Pair<SnippetEntity?, Boolean> ->
        SnippetsUiState(snippets, q, editor.first, editor.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SnippetsUiState())

    fun setQuery(q: String) { query.value = q }

    fun openEditor(snippet: SnippetEntity? = null) {
        editorState.value = snippet to true
    }

    fun closeEditor() {
        editorState.value = null to false
    }

    fun save(title: String, content: String) = viewModelScope.launch {
        val current = editorState.value.first
        if (current == null) {
            repository.create(title.trim(), content.trim())
        } else {
            repository.update(current.copy(title = title.trim(), content = content.trim()))
        }
        closeEditor()
    }

    fun delete(snippet: SnippetEntity) = viewModelScope.launch {
        repository.delete(snippet.id)
    }

    fun recordUsage(id: Long) = viewModelScope.launch {
        repository.recordUsage(id)
    }

    fun expandForCopy(content: String, clipboard: String?): String =
        SnippetExpander.expand(content, clipboard)
}