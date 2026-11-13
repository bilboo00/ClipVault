package com.clipvault.manager.ui.search

import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.clipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: ClipboardRepository
    private lateinit var settings: SettingsManager
    private lateinit var vm: SearchViewModel

    @Before
    fun setUp() {
        repository = mockk()
        settings = mockk()
        every { settings.recentSearches } returns MutableStateFlow(emptyList())
        coJustRun { settings.addRecentSearch(any()) }
        vm = SearchViewModel(repository, settings)
    }

    @Test
    fun debouncedQuery_supersededQueryNeverReachesDao() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            advanceTimeBy(200)
            awaitItem() // settle initial emission
            vm.setQuery("ab")
            advanceTimeBy(100)
            vm.setQuery("")
            advanceTimeBy(300)
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 0) { repository.searchFts(any()) }
    }

    @Test
    fun scopeAndTypeFilters_narrowResults() = runTest(mainRule.testDispatcher) {
        val results = listOf(
            com.clipvault.manager.domain.model.Clip.Companion.fromEntity(
                clipEntity(id = 1, content = "pinned link", type = ClipType.URL, isPinned = true)
            ),
            com.clipvault.manager.domain.model.Clip.Companion.fromEntity(
                clipEntity(id = 2, content = "fav code", type = ClipType.CODE, isFavorite = true)
            ),
            com.clipvault.manager.domain.model.Clip.Companion.fromEntity(
                clipEntity(id = 3, content = "plain text")
            )
        )
        every { repository.searchFts(any()) } returns flowOf(results)
        vm.setQuery("e")

        vm.state.test {
            skipItems(1) // replayed initial value
            advanceTimeBy(200)
            assertEquals(3, awaitItem().results.size)

            vm.setScope(SearchScope.PINNED)
            assertEquals(listOf(1L), awaitItem().results.map { it.id })

            vm.setScope(SearchScope.FAVORITES)
            assertEquals(listOf(2L), awaitItem().results.map { it.id })

            vm.setTypeFilter(ClipType.CODE)
            assertEquals(listOf(2L), awaitItem().results.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun submitSearch_persistsQuery() = runTest(mainRule.testDispatcher) {
        vm.setQuery("  hello  ")
        vm.submitSearch()
        advanceUntilIdle()

        coVerify { settings.addRecentSearch("  hello  ") }
    }

    @Test
    fun flashCopied_clearsAfterTimeout() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            vm.flashCopied(9)
            assertEquals(9L, awaitItem().justCopiedId)
            advanceTimeBy(1_500)
            assertNull(awaitItem().justCopiedId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recordUsage_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.incrementUseCount(4) }

        vm.recordUsage(4)
        advanceUntilIdle()

        coVerify { repository.incrementUseCount(4) }
    }

    @Test
    fun stateCollection_cancelCompletesWithoutLeak() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
    }
}
