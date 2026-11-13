package com.clipvault.manager.ui.home

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.clipvault.manager.FakeClipPagingSource
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.clipEntity
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.repository.UrlPreviewRepository
import com.clipvault.manager.domain.PasteQueueManager
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.testDiffer
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: ClipboardRepository
    private lateinit var settings: SettingsManager
    private lateinit var queueManager: PasteQueueManager
    private lateinit var urlPreviewRepository: UrlPreviewRepository
    private lateinit var context: Context
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        repository = mockk()
        settings = mockk()
        queueManager = mockk()
        urlPreviewRepository = mockk()
        context = mockk()
        every { settings.monitoringEnabled } returns MutableStateFlow(true)
        every { queueManager.items } returns MutableStateFlow(emptyList())
        every { queueManager.currentIndex } returns MutableStateFlow(0)
        every { queueManager.queueFlow } returns MutableStateFlow(
            com.clipvault.manager.data.preferences.PasteQueueData()
        )
        every { repository.observePinned() } returns flowOf(emptyList())
        every { repository.observeCount() } returns flowOf(0)
        every { repository.observeFavoriteCount() } returns flowOf(0)
        vm = HomeViewModel(repository, settings, queueManager, urlPreviewRepository, context)
    }

    @Test
    fun setViewMode_isReflectedInState() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            assertEquals(HomeViewMode.ALL, awaitItem().viewMode)
            vm.setViewMode(HomeViewMode.PINNED)
            assertEquals(HomeViewMode.PINNED, awaitItem().viewMode)
            vm.setViewMode(HomeViewMode.FAVORITES)
            assertEquals(HomeViewMode.FAVORITES, awaitItem().viewMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clipsFlow_pinnedModeUsesPinnedSource() = runTest(mainRule.testDispatcher) {
        val entities = listOf(
            clipEntity(id = 1, content = "pinned one", isPinned = true),
            clipEntity(id = 2, content = "pinned two", isPinned = true)
        )
        every { repository.pagingPinned() } answers {
            kotlinx.coroutines.flow.flowOf(
                androidx.paging.PagingData.from(entities.map(Clip.Companion::fromEntity))
            )
        }
        vm.setViewMode(HomeViewMode.PINNED)

        vm.clipsFlow.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify { repository.pagingPinned() }
    }

    @Test
    fun clipsFlow_allModeEmptySourceYieldsEmptySnapshot() = runTest(mainRule.testDispatcher) {
        every { repository.pagingAllUnpinned() } answers {
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<Clip>())
        }

        vm.clipsFlow.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify { repository.pagingAllUnpinned() }
    }

    @Test
    fun toggleFavorite_flipsFlag() = runTest(mainRule.testDispatcher) {
        val clip = Clip.Companion.fromEntity(clipEntity(id = 5, isFavorite = false))
        coJustRun { repository.toggleFavorite(5, true) }

        vm.toggleFavorite(clip)
        advanceUntilIdle()

        coVerify { repository.toggleFavorite(5, true) }
    }

    @Test
    fun delete_emitsDeletedEventAndUndoRestores() = runTest(mainRule.testDispatcher) {
        val clip = Clip.Companion.fromEntity(clipEntity(id = 5, content = "bye"))
        coJustRun { repository.delete(5) }
        coJustRun { repository.insertForUndo(any()) }

        vm.events.test {
            vm.delete(clip)
            val event = awaitItem()
            assertTrue(event is HomeEvent.Deleted)
            vm.undoDelete((event as HomeEvent.Deleted).clip)
            advanceUntilIdle()
            coVerify { repository.insertForUndo(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectAllThenExit_clearsSelection() = runTest(mainRule.testDispatcher) {
        vm.setLoadedIds(setOf(1L, 2L))
        vm.enterMultiSelect(1L)

        vm.state.test {
            skipItems(1)
            assertEquals(setOf(1L), awaitItem().selectedIds)
            vm.selectAll()
            assertEquals(setOf(1L, 2L), awaitItem().selectedIds)
            vm.exitMultiSelect()
            val cleared = awaitItem()
            assertTrue(cleared.selectedIds.isEmpty())
            assertFalse(cleared.multiSelectMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun flashCopied_setsThenClearsAfterTimeout() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            skipItems(1)
            vm.flashCopied(7)
            assertEquals(7L, awaitItem().justCopiedForId)
            advanceTimeBy(1_500)
            assertNull(awaitItem().justCopiedForId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun fetchUrlTitle_cachesNegativeOnMiss() = runTest(mainRule.testDispatcher) {
        // Both getCached and refresh miss: the URL degrades to no-title and
        // the negative is cached, so a second call never hits the repo again.
        // (Deliberately no `throws` stub here: fetchUrlTitle runs the fetch
        // on Dispatchers.IO, so an exception's arrival time is real-time
        // racy under a virtual scheduler. The null path exercises the same
        // `?: URL_NO_TITLE` negative-cache contract deterministically.)
        coEvery { urlPreviewRepository.getCached("https://x.test") } returns null
        coEvery { urlPreviewRepository.refresh("https://x.test") } returns null

        vm.fetchUrlTitle(1, "https://x.test")
        advanceUntilIdle()

        // Negative cached: second call must not hit the repository again.
        vm.fetchUrlTitle(1, "https://x.test")
        advanceUntilIdle()
        coVerify(exactly = 1) { urlPreviewRepository.refresh("https://x.test") }
    }

    @Test
    fun showCachedUrlTitle_localOnly_neverTouchesNetwork() = runTest(mainRule.testDispatcher) {
        // Display path (row becomes visible): resolves from the local Room
        // cache only — per PRIVACY_POLICY.md the page-title fetch happens
        // exclusively when the user taps the entry (fetchUrlTitle).
        coEvery { urlPreviewRepository.peekCached("https://a.test") } returns "Local title"
        coEvery { urlPreviewRepository.peekCached("https://miss.test") } returns null

        vm.showCachedUrlTitle(1, "https://a.test")
        vm.showCachedUrlTitle(2, "https://miss.test")
        advanceUntilIdle()

        assertEquals("Local title", vm.titleMap[1])
        assertNull(vm.titleMap[2])

        // The TTL-checked and network-capable methods are never invoked.
        coVerify(exactly = 0) { urlPreviewRepository.getCached(any()) }
        coVerify(exactly = 0) { urlPreviewRepository.refresh(any()) }
    }

    /** Stubs a primaryClip whose description carries EXTRA_IS_SENSITIVE. */
    private fun stubClipboard(text: String, sensitive: Boolean) {
        val extras = mockk<PersistableBundle>()
        every { extras.getBoolean("android.content.extra.IS_SENSITIVE", false) } returns sensitive
        val desc = mockk<ClipDescription>()
        every { desc.extras } returns extras
        val clip = mockk<ClipData>()
        every { clip.description } returns desc
        every { clip.itemCount } returns 1
        val item = mockk<ClipData.Item>()
        every { clip.getItemAt(0) } returns item
        every { item.coerceToText(any()) } returns text
        val cm = mockk<ClipboardManager>()
        every { cm.primaryClip } returns clip
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns cm
    }

    @Test
    fun saveCurrentClipboardNow_sensitiveClipIsNeverSaved() = runTest(mainRule.testDispatcher) {
        stubClipboard(text = "hunter2", sensitive = true)

        vm.events.test {
            vm.saveCurrentClipboardNow(silent = false)
            assertTrue(awaitItem() is HomeEvent.SensitiveSkipped)
            cancelAndIgnoreRemainingEvents()
        }

        // No save and no suppression bookkeeping at all for sensitive clips.
        coVerify(exactly = 0) { repository.saveIfNew(any(), any()) }
        verify(exactly = 0) { repository.isContentSuppressed(any()) }
        verify(exactly = 0) { repository.clearDeleteSuppressions() }
    }

    @Test
    fun saveCurrentClipboardNow_regularClipStillSaves() = runTest(mainRule.testDispatcher) {
        stubClipboard(text = "hello world", sensitive = false)
        every { repository.isContentSuppressed("hello world") } returns false
        every { repository.clearDeleteSuppressions() } just Runs
        coEvery { repository.saveIfNew("hello world", "fab") } returns 7L

        vm.events.test {
            vm.saveCurrentClipboardNow(silent = false)
            val event = awaitItem()
            assertTrue(event is HomeEvent.SavedNew)
            assertTrue((event as HomeEvent.SavedNew).success)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { repository.saveIfNew("hello world", "fab") }
    }

    @Test
    fun clipsCollection_cancelCompletesWithoutLeak() = runTest(mainRule.testDispatcher) {
        every { repository.pagingAllUnpinned() } answers {
            kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<Clip>())
        }

        vm.clipsFlow.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
    }
}
