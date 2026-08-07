package com.clipvault.manager.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.repository.ClipboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val totalBytes: Long = 0L,
    val typeBreakdown: List<Pair<ClipType, Int>> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: ClipboardRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    init {
        loadStats()
    }

    fun loadStats() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true)
        val now = System.currentTimeMillis()
        // Local-midnight boundaries (UTC math was off by the timezone offset).
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val startOfWeek = startOfToday -
            (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1) * 86_400_000L

        val total = repository.count()
        val today = repository.countSince(startOfToday)
        val week = repository.countSince(startOfWeek)
        val bytes = repository.totalContentBytes()
        val types = repository.countByType()

        _state.value = StatsUiState(
            totalCount = total,
            todayCount = today,
            weekCount = week,
            totalBytes = bytes,
            typeBreakdown = types,
            isLoading = false
        )
    }
}
