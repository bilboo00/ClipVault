package com.clipvault.manager.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.security.BiometricManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.domain.PasteQueueManager
import com.clipvault.manager.domain.model.Clip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val unlocked: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClipDetailViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val clipDao: ClipDao,
    private val queueManager: PasteQueueManager,
    val biometricManager: BiometricManager
) : ViewModel() {

    private val idFlow = MutableStateFlow(0L)
    private val unlockedSet = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<ClipDetailState> = idFlow
        .flatMapLatest { id ->
            if (id == 0L) flowOf(ClipDetailState(loading = false))
            else repository.observeById(id).map { clip ->
                val unlocked = unlockedSet.value.contains(id) || clip?.isLocked == false
                ClipDetailState(clip = clip, loading = false, unlocked = unlocked)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClipDetailState())

    fun load(id: Long) {
        idFlow.value = id
    }

    fun togglePin(clip: Clip) = viewModelScope.launch {
        repository.togglePin(clip.id, !clip.isPinned)
    }

    fun delete(clip: Clip, onDone: () -> Unit) = viewModelScope.launch {
        repository.delete(clip.id)
        onDone()
    }

    fun setNotes(notes: String) = viewModelScope.launch {
        state.value.clip?.let { repository.setNotes(it.id, notes) }
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
        if (!newLocked) unlockedSet.value = unlockedSet.value + clip.id
        onResult(newLocked)
    }

    fun unlock(clip: Clip, onSuccess: () -> Unit) {
        unlockedSet.value = unlockedSet.value + clip.id
        onSuccess()
    }

    fun relock(clip: Clip) {
        unlockedSet.value = unlockedSet.value - clip.id
    }
}