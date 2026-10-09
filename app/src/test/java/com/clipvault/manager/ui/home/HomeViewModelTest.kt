package com.clipvault.manager.ui.home

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.repository.UrlPreviewRepository
import com.clipvault.manager.domain.PasteQueueManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        vm = HomeViewModel(repository, settings, queueManager, urlPreviewRepository, context)
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
}
