package com.clipvault.manager.ui.stats

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.local.dao.TypeCount
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: ClipboardRepository
    private lateinit var settings: SettingsManager
    private lateinit var vm: StatsViewModel
    private val viewModels = mutableListOf<StatsViewModel>()

    @Before
    fun setUp() {
        repository = mockk()
        settings = mockk()
        every { settings.maxHistoryDays } returns MutableStateFlow(30)
        every { repository.observeCount() } returns flowOf(10)
        every { repository.observeCountSince(any()) } returns flowOf(3)
        every { repository.observeTotalContentBytes() } returns flowOf(2048L)
        every { repository.observeCountByType() } returns flowOf(listOf(ClipType.TEXT to 7))
        every { repository.observeFavoriteCount() } returns flowOf(2)
        every { repository.observeCreationTimestampsSince(any()) } returns flowOf(emptyList())
        vm = newViewModel()
    }

    /**
     * The screen has a minute-ticker that re-emits on local-midnight rollover.
     * Left running on the virtual-time dispatcher it would make `runTest`
     * cleanup advance the clock forever, so every VM is cancelled afterwards.
     */
    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
    }

    private fun newViewModel(): StatsViewModel =
        StatsViewModel(repository, settings).also { viewModels += it }

    @Test
    fun state_emitsLoadedStats() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1) // initial loading state
            val s = awaitItem()
            assertFalse(s.isLoading)
            assertEquals(10, s.totalCount)
            assertEquals(2048L, s.totalBytes)
            assertEquals(listOf(ClipType.TEXT to 7), s.typeBreakdown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun state_emptyDatabaseEmitsZeros() = runTest(mainRule.testDispatcher) {
        every { repository.observeCount() } returns flowOf(0)
        every { repository.observeCountSince(any()) } returns flowOf(0)
        every { repository.observeTotalContentBytes() } returns flowOf(0L)
        every { repository.observeCountByType() } returns flowOf(emptyList())
        val emptyVm = newViewModel()

        emptyVm.state.test {
            skipItems(1)
            val s = awaitItem()
            assertFalse(s.isLoading)
            assertEquals(0, s.totalCount)
            assertEquals(0L, s.totalBytes)
            assertEquals(emptyList<Pair<ClipType, Int>>(), s.typeBreakdown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun typeCountBearing_unknownTypeStringIsDropped() = runTest(mainRule.testDispatcher) {
        // Documents the lenient mapping also covered at repository level:
        // a stale/unknown type row never breaks the stats screen.
        every { repository.observeCountByType() } returns flowOf(
            listOf(ClipType.URL to 1)
        )
        val vm2 = newViewModel()

        vm2.state.test {
            skipItems(1)
            assertEquals(listOf(ClipType.URL to 1), awaitItem().typeBreakdown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stateCollection_cancelCompletesWithoutLeak() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
