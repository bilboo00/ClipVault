package com.clipvault.manager.ui.stats

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

/** Time horizon selected by the Stats chip row. */
enum class StatsPeriod { SEVEN_DAYS, THIRTY_DAYS, ALL_TIME }

/** One column of the daily-activity chart. */
data class DayBar(val label: String, val count: Int, val isToday: Boolean)

/** Transient state of the "prune old clips" action. */
data class PruneUiState(val running: Boolean = false, val done: Boolean = false)

data class StatsUiState(
    val isLoading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.SEVEN_DAYS,
    val totalCount: Int = 0,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val sevenDayCount: Int = 0,
    val thirtyDayCount: Int = 0,
    val favoriteCount: Int = 0,
    val totalBytes: Long = 0L,
    val dailyBars: List<DayBar> = emptyList(),
    val typeBreakdown: List<Pair<ClipType, Int>> = emptyList(),
    val retentionDays: Int = 30
) {
    /** Hero count for the currently selected chip. */
    val periodCount: Int
        get() = when (period) {
            StatsPeriod.SEVEN_DAYS -> sevenDayCount
            StatsPeriod.THIRTY_DAYS -> thirtyDayCount
            StatsPeriod.ALL_TIME -> totalCount
        }

    val averagePerDay: Float
        get() = if (dailyBars.isEmpty()) 0f
        else dailyBars.sumOf { it.count }.toFloat() / dailyBars.size

    val peakCount: Int
        get() = dailyBars.maxOfOrNull { it.count } ?: 0
}

/** Window counts for the current day/week/period chips. */
private data class WindowCounts(
    val total: Int,
    val seven: Int,
    val thirty: Int,
    val today: Int,
    val week: Int
)

/** Non-count extras shown in the lower cards. */
private data class Extras(
    val bytes: Long,
    val types: List<Pair<ClipType, Int>>,
    val favorites: Int
)

/**
 * Local-midnight boundary snapshot. Re-emitted (via distinctUntilChanged) only
 * when a boundary actually moves, so the underlying DAO flows are re-collected
 * the moment the "Today" / "This week" window (or the seven-day chart window)
 * rolls over. The current wall-clock time deliberately isn't part of the
 * record — including it would defeat the de-duplication and restart every DAO
 * query once a minute.
 */
private data class DayBounds(
    val today: Long,
    val week: Long,
    val sevenDays: Long,
    val thirtyDays: Long,
    /** Seven local midnights, oldest → today (the chart's window). */
    val dayStarts: List<Long>
)

private val DOW_LETTERS = charArrayOf('S', 'M', 'T', 'W', 'T', 'F', 'S')

private fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ts
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDaysAgo(now: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -days)
}.timeInMillis

private fun boundsOf(now: Long): DayBounds {
    // Local-midnight boundaries (UTC math was off by the timezone offset).
    val today = startOfDay(now)
    // Walk back with Calendar.add so DST transitions can't shift the window.
    val dayStarts = ArrayList<Long>(7)
    val cal = Calendar.getInstance().apply { timeInMillis = today }
    repeat(7) {
        dayStarts.add(0, cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    val week = today - (Calendar.getInstance().apply { timeInMillis = now }
        .get(Calendar.DAY_OF_WEEK) - 1) * 86_400_000L
    return DayBounds(
        today = today,
        week = week,
        sevenDays = dayStarts.first(),
        thirtyDays = startOfDaysAgo(now, 29),
        dayStarts = dayStarts
    )
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel @Inject constructor(
    private val repository: ClipboardRepository,
    private val settings: SettingsManager
) : ViewModel() {

    private val boundaryTicker = flow {
        while (true) {
            emit(boundsOf(System.currentTimeMillis()))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    private val _period = MutableStateFlow(StatsPeriod.SEVEN_DAYS)

    fun setPeriod(period: StatsPeriod) {
        _period.value = period
    }

    private val _prune = MutableStateFlow(PruneUiState())
    val pruneState: StateFlow<PruneUiState> = _prune

    /**
     * Live stats: every DAO flow re-emits when the clips table changes, so the
     * counters update in real time while the screen is visible — no restart
     * needed. The ticker handles midnight / week rollover. Period and
     * retention live in the innermost combine so switching chips doesn't
     * re-subscribe the DAO queries.
     */
    val state: StateFlow<StatsUiState> = boundaryTicker
        .flatMapLatest { bounds ->
            val countsFlow: Flow<WindowCounts> = combine(
                repository.observeCount(),
                repository.observeCountSince(bounds.sevenDays),
                repository.observeCountSince(bounds.thirtyDays),
                repository.observeCountSince(bounds.today),
                repository.observeCountSince(bounds.week)
            ) { total, seven, thirty, today, week ->
                WindowCounts(total = total, seven = seven, thirty = thirty, today = today, week = week)
            }

            val extrasFlow: Flow<Extras> = combine(
                repository.observeTotalContentBytes(),
                repository.observeCountByType(),
                repository.observeFavoriteCount()
            ) { bytes, types, favorites ->
                Extras(bytes = bytes, types = types, favorites = favorites)
            }

            val timestampsFlow = repository.observeCreationTimestampsSince(bounds.dayStarts.first())

            combine(
                combine(countsFlow, extrasFlow, timestampsFlow) { counts, extras, timestamps ->
                    Triple(counts, extras, timestamps)
                },
                _period,
                settings.maxHistoryDays
            ) { base, period, retention ->
                val (counts, extras, timestamps) = base
                StatsUiState(
                    isLoading = false,
                    period = period,
                    totalCount = counts.total,
                    todayCount = counts.today,
                    weekCount = counts.week,
                    sevenDayCount = counts.seven,
                    thirtyDayCount = counts.thirty,
                    favoriteCount = extras.favorites,
                    totalBytes = extras.bytes,
                    dailyBars = bucketize(timestamps, bounds.dayStarts),
                    typeBreakdown = extras.types,
                    retentionDays = retention
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /**
     * Bucket [timestamps] into the seven local days of [dayStarts]. Each
     * timestamp belongs to the newest day-start it's not older than; anything
     * beyond the window (clock skew) is ignored.
     */
    private fun bucketize(timestamps: List<Long>, dayStarts: List<Long>): List<DayBar> {
        val counts = IntArray(dayStarts.size)
        for (ts in timestamps) {
            val index = dayStarts.indexOfLast { it <= ts }
            if (index >= 0) counts[index]++
        }
        val cal = Calendar.getInstance()
        return dayStarts.mapIndexed { index, start ->
            cal.timeInMillis = start
            DayBar(
                label = DOW_LETTERS[cal.get(Calendar.DAY_OF_WEEK) - 1].toString(),
                count = counts[index],
                isToday = index == dayStarts.lastIndex
            )
        }
    }

    /**
     * Manually prunes clips older than the configured retention window —
     * the same bounded, batched delete the automatic pass uses. Pinned clips
     * are always kept. Runs once, then flashes a "done" state for a moment.
     */
    fun pruneOld() = viewModelScope.launch {
        val days = state.value.retentionDays
        if (days <= 0 || _prune.value.running || _prune.value.done) return@launch
        _prune.value = PruneUiState(running = true)
        try {
            val cutoff = System.currentTimeMillis() - days * 86_400_000L
            withContext(Dispatchers.IO) { repository.pruneOlderThan(cutoff) }
            _prune.value = PruneUiState(done = true)
            delay(2_200L)
        } catch (e: Exception) {
            Log.e(TAG, "pruneOld failed", e)
        } finally {
            _prune.value = PruneUiState()
        }
    }

    private companion object {
        const val TAG = "StatsViewModel"
    }
}
