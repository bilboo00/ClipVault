package com.clipvault.manager.ui.collections

import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.local.dao.CollectionDao
import com.clipvault.manager.data.local.dao.CollectionUsageCount
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.data.repository.CollectionRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.just
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: CollectionRepository
    private lateinit var collectionDao: CollectionDao
    private lateinit var vm: CollectionsViewModel

    @Before
    fun setUp() {
        repository = mockk()
        collectionDao = mockk()
        every { repository.observeAll() } returns flowOf(
            listOf(CollectionEntity(id = 1, name = "work"))
        )
        every { collectionDao.observeUsageCounts() } returns flowOf(
            listOf(CollectionUsageCount(1, 5))
        )
        vm = CollectionsViewModel(repository, collectionDao)
    }

    @Test
    fun state_combinesCollectionsWithUsage() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(1, s.collections.size)
            assertEquals(mapOf(1L to 5), s.usageCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createCollection_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        coEvery { repository.create("work") } returns 1L

        vm.createCollection("work")
        advanceUntilIdle()

        coVerify { repository.create("work") }
    }

    @Test
    fun deleteCollection_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.delete(1) }

        vm.deleteCollection(1)
        advanceUntilIdle()

        coVerify { repository.delete(1) }
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
