package com.clipvault.manager.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.dao.TagDao
import com.clipvault.manager.data.local.entity.TagEntity
import com.clipvault.manager.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagsUiState(
    val tags: List<TagEntity> = emptyList(),
    val usageCount: Map<Long, Int> = emptyMap()
)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val repository: TagRepository,
    private val tagDao: TagDao
) : ViewModel() {

    val state: StateFlow<TagsUiState> = combine(
        repository.observeAll(),
        tagDao.observeUsageCounts()
    ) { tags, usage ->
        TagsUiState(tags, usage.associate { it.tagId to it.count })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    fun createTag(name: String, color: String) = viewModelScope.launch {
        repository.create(name, color)
    }

    fun updateTag(tag: TagEntity, name: String, color: String) = viewModelScope.launch {
        repository.update(tag.copy(name = name.trim(), color = color))
    }

    fun deleteTag(id: Long) = viewModelScope.launch {
        repository.delete(id)
    }
}