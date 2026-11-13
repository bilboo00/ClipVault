package com.clipvault.manager.ui.snippets

import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.local.entity.SnippetEntity
import com.clipvault.manager.data.repository.SnippetRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: SnippetRepository
    private lateinit var vm: SnippetsViewModel

    private val first = SnippetEntity(id = 1, title = "mail", content = "hi {{clipboard}}")
    private val second = SnippetEntity(id = 2, title = "addr", content = "home")

    @Before
    fun setUp() {
        repository = mockk()
        every { repository.observeAll() } returns flowOf(listOf(first, second))
        every { repository.search(any()) } returns flowOf(listOf(second))
        vm = SnippetsViewModel(repository)
    }

    @Test
    fun setQuery_switchesToSearchResults() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            vm.setQuery("addr")
            val s = awaitItem()
            assertEquals("addr", s.query)
            assertEquals(listOf(second), s.snippets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveNew_trimsAndCreatesThenClosesEditor() = runTest(mainRule.testDispatcher) {
        coEvery { repository.create("mail", "hi") } returns 9L

        vm.state.test {
            skipItems(1)
            vm.openEditor()
            assertTrue(awaitItem().showEditor)
            vm.save("  mail  ", "  hi  ")
            advanceUntilIdle()
            coVerify { repository.create("mail", "hi") }
            assertFalse(awaitItem().showEditor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveBlank_doesNotPersistAndKeepsEditorOpen() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            vm.openEditor()
            assertTrue(awaitItem().showEditor)
            vm.save("   ", "content")
            advanceUntilIdle()
            coVerify(exactly = 0) { repository.create(any(), any()) }
            coVerify(exactly = 0) { repository.update(any()) }
            assertTrue(vm.state.value.showEditor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveExisting_updatesCopy() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.update(any()) }
        vm.openEditor(first)
        advanceUntilIdle()

        vm.save("mail2", "hi2")
        advanceUntilIdle()

        coVerify { repository.update(match { it.id == 1L && it.title == "mail2" && it.content == "hi2" }) }
    }

    @Test
    fun delete_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.delete(2) }

        vm.delete(second)
        advanceUntilIdle()

        coVerify { repository.delete(2) }
    }

    @Test
    fun expandForCopy_expandsClipboardToken() {
        assertEquals("hi there!", vm.expandForCopy("hi {{clipboard}}", "there!"))
    }

    @Test
    fun stateCollection_cancelCompletesWithoutLeak() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
