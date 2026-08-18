package com.clipvault.manager.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.repository.ClipboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class StatsUiState(
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val totalBytes: Long = 0L,
    val typeBreakdown: List<Pair<ClipType, Int>> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Day/week boundary snapshot. Re-emitted (via distinctUntilChanged) only when
 * a boundary actually moves, so the underlying flows are re-collected the
 * moment the "Today" / "This week" window rolls over.
 */
private data class DayBounds(val now: Long, val today: Long, val week: Long)

private fun boundsOf(now: Long): DayBounds {
    // Local-midnight boundaries (UTC math was off by the timezone offset).
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis
    val week = today - (cal.get(Calendar.DAY_OF_WEEK) - 1) * 86_400_000L
    return DayBounds(now, today, week)
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    private val repository: ClipboardRepository
) : ViewModel() {

    private val boundaryTicker = flow {
        while (true) {
            emit(boundsOf(System.currentTimeMillis()))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    /**
     * Live stats: every DAO flow re-emits when the clips table changes, so the
     * counters update in real time while the screen is visible — no restart
     * needed. The ticker handles midnight / week rollover.
     */
    val state: StateFlow<StatsUiState> = boundaryTicker
        .flatMapLatest { bounds ->
            combine(
                repository.observeCount(),
                repository.observeCountSince(bounds.today),
                repository.observeCountSince(bounds.week),
                repository.observeTotalContentBytes(),
                repository.observeCountByType()
            ) { total, today, week, bytes, types ->
                StatsUiState(
                    totalCount = total,
                    todayCount = today,
                    weekCount = week,
                    totalBytes = bytes,
                    typeBreakdown = types,
                    isLoading = false
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
