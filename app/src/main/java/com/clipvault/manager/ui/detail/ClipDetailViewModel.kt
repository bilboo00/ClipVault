package com.clipvault.manager.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.data.local.entity.TagEntity
import com.clipvault.manager.data.security.BiometricManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.repository.CollectionRepository
import com.clipvault.manager.data.repository.TagRepository
import com.clipvault.manager.domain.PasteQueueManager
import com.clipvault.manager.domain.model.Clip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClipDetailState(
    val clip: Clip? = null,
    val loading: Boolean = true,
    /** True when the user has unlocked a locked clip in this session. */
    val unlocked: Boolean = false,
    val tagIds: Set<Long> = emptySet(),
    val collectionIds: Set<Long> = emptySet(),
    val tags: List<TagEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClipDetailViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val clipDao: ClipDao,
    private val queueManager: PasteQueueManager,
    private val tagRepository: TagRepository,
    private val collectionRepository: CollectionRepository,
    val biometricManager: BiometricManager
) : ViewModel() {

    private val idFlow = MutableStateFlow(0L)
    private val unlockedSet = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<ClipDetailState> = idFlow
        .flatMapLatest { id ->
            if (id == 0L) flowOf(ClipDetailState(loading = false))
            else combine(
                repository.observeById(id),
                tagRepository.observeCrossRefsForClip(id),
                collectionRepository.observeCrossRefsForClip(id),
                tagRepository.observeAll(),
                collectionRepository.observeAll()
            ) { clip, tagIds, collectionIds, tags, collections ->
                val unlocked = unlockedSet.value.contains(id) || clip?.isLocked == false
                ClipDetailState(
                    clip = clip,
                    loading = false,
                    unlocked = unlocked,
                    tagIds = tagIds,
                    collectionIds = collectionIds,
                    tags = tags,
                    collections = collections
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClipDetailState())

    fun load(id: Long) {
        idFlow.value = id
    }

    fun togglePin(clip: Clip) = viewModelScope.launch {
        repository.togglePin(clip.id, !clip.isPinned)
    }

    fun toggleFavorite(clip: Clip) = viewModelScope.launch {
        repository.toggleFavorite(clip.id, !clip.isFavorite)
    }

    fun toggleTag(tagId: Long) = viewModelScope.launch {
        val clipId = state.value.clip?.id ?: return@launch
        val current = state.value.tagIds
        val next = if (tagId in current) current - tagId else current + tagId
        tagRepository.setTagsForClip(clipId, next.toList())
    }

    fun toggleCollection(collectionId: Long) = viewModelScope.launch {
        val clipId = state.value.clip?.id ?: return@launch
        val current = state.value.collectionIds
        val next = if (collectionId in current) current - collectionId else current + collectionId
        collectionRepository.setCollectionsForClip(clipId, next.toList())
    }

    fun delete(clip: Clip, onDone: () -> Unit) = viewModelScope.launch {
        repository.delete(clip.id)
        onDone()
    }

    fun setNotes(notes: String) = viewModelScope.launch {
        state.value.clip?.let { repository.setNotes(it.id, notes) }
    }

    fun setExpiration(expiresAt: Long?) = viewModelScope.launch {
        state.value.clip?.let { repository.setExpiration(it.id, expiresAt) }
    }

    fun setUseLimit(useLimit: Int?) = viewModelScope.launch {
        state.value.clip?.let { repository.setUseLimit(it.id, useLimit) }
    }

    fun replaceContent(newContent: String) = viewModelScope.launch {
        state.value.clip?.let { repository.updateText(it.id, newContent) }
    }

    fun addToQueue() = viewModelScope.launch {
        state.value.clip?.let {
            val entity = clipDao.getById(it.id) ?: return@launch
            queueManager.addToQueue(entity)
        }
    }

    fun toggleLock(clip: Clip, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val newLocked = !clip.isLocked
        repository.setLocked(clip.id, newLocked)
        // Keep the session-unlock set consistent with the DB state so the UI
        // actually reflects the new lock status immediately.
        unlockedSet.value = if (newLocked) unlockedSet.value - clip.id
        else unlockedSet.value + clip.id
        onResult(newLocked)
    }

    fun unlock(clip: Clip, onSuccess: () -> Unit) {
        unlockedSet.value = unlockedSet.value + clip.id
        onSuccess()
    }

    fun relock(clip: Clip) {
        unlockedSet.value = unlockedSet.value - clip.id
    }

    fun recordUsage() = viewModelScope.launch {
        state.value.clip?.let { repository.incrementUseCount(it.id) }
    }
}