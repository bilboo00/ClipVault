package com.clipvault.manager.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.dao.CollectionDao
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.data.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionsUiState(
    val collections: List<CollectionEntity> = emptyList(),
    val usageCount: Map<Long, Int> = emptyMap()
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repository: CollectionRepository,
    private val collectionDao: CollectionDao
) : ViewModel() {

    val state: StateFlow<CollectionsUiState> = combine(
        repository.observeAll(),
        collectionDao.observeUsageCounts()
    ) { collections, usage ->
        CollectionsUiState(collections, usage.associate { it.collectionId to it.count })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionsUiState())

    fun createCollection(name: String) = viewModelScope.launch {
        repository.create(name)
    }

    fun deleteCollection(id: Long) = viewModelScope.launch {
        repository.delete(id)
    }
}