package com.clipvault.manager.ui.tags

import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.local.dao.TagDao
import com.clipvault.manager.data.local.dao.TagUsageCount
import com.clipvault.manager.data.local.entity.TagEntity
import com.clipvault.manager.data.repository.TagRepository
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
class TagsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: TagRepository
    private lateinit var tagDao: TagDao
    private lateinit var vm: TagsViewModel

    @Before
    fun setUp() {
        repository = mockk()
        tagDao = mockk()
        every { repository.observeAll() } returns flowOf(
            listOf(TagEntity(id = 1, name = "work"), TagEntity(id = 2, name = "home"))
        )
        every { tagDao.observeUsageCounts() } returns flowOf(listOf(TagUsageCount(1, 3)))
        vm = TagsViewModel(repository, tagDao)
    }

    @Test
    fun state_combinesTagsWithUsage() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(2, s.tags.size)
            assertEquals(mapOf(1L to 3), s.usageCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun createTag_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        coEvery { repository.create("work", "#4F46E5") } returns 1L

        vm.createTag("work", "#4F46E5")
        advanceUntilIdle()

        coVerify { repository.create("work", "#4F46E5") }
    }

    @Test
    fun updateTag_trimsName() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.update(any()) }

        vm.updateTag(TagEntity(id = 1, name = "old"), "  new  ", "#fff")
        advanceUntilIdle()

        coVerify { repository.update(match { it.name == "new" }) }
    }

    @Test
    fun deleteTag_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.delete(2) }

        vm.deleteTag(2)
        advanceUntilIdle()

        coVerify { repository.delete(2) }
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
