package com.clipvault.manager.data.repository

import android.content.Context
import android.database.SQLException
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.clipvault.manager.FakeClipPagingSource
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.clipEntity
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.dao.TypeCount
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.testDiffer
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var dao: ClipDao
    private lateinit var settings: SettingsManager
    private lateinit var context: Context
    private lateinit var repo: ClipboardRepository

    @Before
    fun setUp() {
        dao = mockk()
        settings = mockk()
        context = mockk()
        every { context.filesDir } returns tmp.root
        every { settings.maxImageBytes } returns MutableStateFlow(Int.MAX_VALUE)
        repo = ClipboardRepository(dao, settings, context)
    }

    // ── saveIfNew ────────────────────────────────────────────────────

    @Test
    fun saveIfNew_savesNewTextClip() = runTest(mainRule.testDispatcher) {
        coEvery { dao.countByContent("hello") } returns 0
        coEvery { dao.insert(any()) } returns 42L

        val id = repo.saveIfNew("  hello  ")

        assertEquals(42L, id)
        coVerify {
            dao.insert(match {
                it.content == "hello" && it.type == ClipType.TEXT && it.expiresAt == null
            })
        }
    }

    @Test
    fun saveIfNew_blankReturnsNullWithoutTouchingDb() = runTest(mainRule.testDispatcher) {
        assertNull(repo.saveIfNew("   "))
        coVerify(exactly = 0) { dao.countByContent(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun saveIfNew_duplicateReturnsNull() = runTest(mainRule.testDispatcher) {
        coEvery { dao.countByContent("dup") } returns 2

        assertNull(repo.saveIfNew("dup"))
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun saveIfNew_suppressedAfterDeleteReturnsNull() = runTest(mainRule.testDispatcher) {
        val entity = clipEntity(id = 1, content = "gone")
        coEvery { dao.getById(1) } returns entity
        coJustRun { dao.deleteById(1) }

        repo.delete(1)
        assertTrue(repo.isContentSuppressed("gone"))
        assertNull(repo.saveIfNew("gone"))
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun saveIfNew_otpClipGetsExpiry() = runTest(mainRule.testDispatcher) {
        val before = System.currentTimeMillis()
        coEvery { dao.countByContent(any()) } returns 0
        coEvery { dao.insert(any()) } returns 7L

        val id = repo.saveIfNew("Your verification code is 482913")

        assertEquals(7L, id)
        coVerify {
            dao.insert(match {
                it.type == ClipType.OTP &&
                    (it.expiresAt ?: 0L) in before..(System.currentTimeMillis() + 600_000L + 5_000L)
            })
        }
    }

    // ── updateText: hidden bug — blank replacement was written through ──

    @Test
    fun updateText_blankContentIsIgnored() = runTest(mainRule.testDispatcher) {
        coEvery { dao.getById(3) } returns clipEntity(id = 3, content = "old")

        repo.updateText(3, "   ")

        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun updateText_reclassifiesEditedContent() = runTest(mainRule.testDispatcher) {
        coEvery { dao.getById(3) } returns clipEntity(id = 3, content = "https://x.test", type = ClipType.URL)
        coJustRun { dao.update(any()) }

        repo.updateText(3, "just words now")

        coVerify {
            dao.update(match { it.content == "just words now" && it.type == ClipType.TEXT })
        }
    }

    // ── delete / undo ────────────────────────────────────────────────

    @Test
    fun delete_suppressesContentAndDeletesRow() = runTest(mainRule.testDispatcher) {
        val entity = clipEntity(id = 1, content = "gone")
        coEvery { dao.getById(1) } returns entity
        coJustRun { dao.deleteById(1) }

        repo.delete(1)

        coVerify { dao.deleteById(1) }
        assertTrue(repo.isContentSuppressed("gone"))
    }

    @Test
    fun insertForUndo_restoresRow() = runTest(mainRule.testDispatcher) {
        val entity = clipEntity(id = 9, content = "back")
        coEvery { dao.restore(entity) } returns 9L

        repo.insertForUndo(entity)

        coVerify { dao.restore(entity) }
    }

    @Test
    fun clearDeleteSuppressions_allowsResave() = runTest(mainRule.testDispatcher) {
        val entity = clipEntity(id = 1, content = "gone")
        coEvery { dao.getById(1) } returns entity
        coJustRun { dao.deleteById(1) }
        coEvery { dao.countByContent("gone") } returns 0
        coEvery { dao.insert(any()) } returns 11L

        repo.delete(1)
        repo.clearDeleteSuppressions()

        assertFalse(repo.isContentSuppressed("gone"))
        assertEquals(11L, repo.saveIfNew("gone"))
    }

    @Test
    fun bulkDelete_suppressesAllAndReturnsCount() = runTest(mainRule.testDispatcher) {
        val entities = listOf(clipEntity(id = 1, content = "a"), clipEntity(id = 2, content = "b"))
        coEvery { dao.getByIds(listOf(1, 2)) } returns entities
        coEvery { dao.deleteByIds(listOf(1, 2)) } returns 2

        assertEquals(2, repo.bulkDelete(listOf(1, 2)))
        assertTrue(repo.isContentSuppressed("a"))
        assertTrue(repo.isContentSuppressed("b"))
    }

    // ── pins / favorites ─────────────────────────────────────────────

    @Test
    fun togglePin_pinsToTopWithDistinctOrder() = runTest(mainRule.testDispatcher) {
        coEvery { dao.minPinnedSortOrder() } returns 5
        coJustRun { dao.setPinned(1, true) }
        coJustRun { dao.setSortOrder(1, 4) }

        repo.togglePin(1, true)

        coVerify { dao.setPinned(1, true) }
        coVerify { dao.setSortOrder(1, 4) }
    }

    @Test
    fun toggleFavorite_delegates() = runTest(mainRule.testDispatcher) {
        coJustRun { dao.setFavorite(2, true) }

        repo.toggleFavorite(2, true)

        coVerify { dao.setFavorite(2, true) }
    }

    // ── prune ────────────────────────────────────────────────────────

    @Test
    fun pruneExpired_suppressesThenPrunesAndReturnsCount() = runTest(mainRule.testDispatcher) {
        val expired = listOf(clipEntity(id = 1, content = "old1"), clipEntity(id = 2, content = "old2"))
        coEvery { dao.getExpiredBefore(any()) } returns expired
        coEvery { dao.pruneExpired(any()) } returns 2

        assertEquals(2, repo.pruneExpired())

        assertTrue(repo.isContentSuppressed("old1"))
        coVerify { dao.pruneExpired(any()) }
    }

    @Test
    fun pruneOlderThan_suppressesThenDeletes() = runTest(mainRule.testDispatcher) {
        coEvery { dao.getOlderThan(1000L) } returns listOf(clipEntity(id = 3, content = "stale"))
        coEvery { dao.deleteOlderThan(1000L) } returns 1

        repo.pruneOlderThan(1000L)

        assertTrue(repo.isContentSuppressed("stale"))
        coVerify { dao.deleteOlderThan(1000L) }
    }

    // ── observe mapping ─────────────────────────────────────────────

    @Test
    fun observePinned_mapsEntitiesToClips() = runTest(mainRule.testDispatcher) {
        every { dao.observePinned() } returns flowOf(
            listOf(clipEntity(id = 1, content = "p", isPinned = true))
        )

        repo.observePinned().test {
            val clips = awaitItem()
            assertEquals(1, clips.size)
            assertEquals("p", clips[0].content)
            assertTrue(clips[0].isPinned)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeById_nullEntityMapsToNullClip() = runTest(mainRule.testDispatcher) {
        every { dao.observeById(99) } returns flowOf(null)

        repo.observeById(99).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeCountByType_dropsUnparsableRows() = runTest(mainRule.testDispatcher) {
        every { dao.observeCountByType() } returns flowOf(
            listOf(TypeCount("TEXT", 2), TypeCount("BOGUS", 9))
        )

        repo.observeCountByType().test {
            assertEquals(listOf(ClipType.TEXT to 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeTotalContentBytes_nullMapsToZero() = runTest(mainRule.testDispatcher) {
        every { dao.observeTotalContentLength() } returns flowOf(null)

        repo.observeTotalContentBytes().test {
            assertEquals(0L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── paging ───────────────────────────────────────────────────────

    @Test
    fun pagingAllUnpinned_emitsMappedClips() = runTest(mainRule.testDispatcher) {
        val entities = listOf(
            clipEntity(id = 1, content = "one"),
            clipEntity(id = 2, content = "https://x.test", type = ClipType.URL)
        )
        every { dao.pagingSourceAllUnpinned() } answers { FakeClipPagingSource(entities) }

        val items = repo.pagingAllUnpinned().asSnapshot()

        assertEquals(2, items.size)
        assertEquals("one", items[0].content)
        assertEquals(ClipType.URL, items[1].type)
    }

    @Test
    fun pagingPinned_emptySourceYieldsEmptySnapshot() = runTest(mainRule.testDispatcher) {
        every { dao.pagingSourcePinned() } answers { FakeClipPagingSource(emptyList()) }

        assertTrue(repo.pagingPinned().asSnapshot().isEmpty())
    }

    @Test
    fun pagingSearch_blankQueryYieldsEmptyWithoutTouchingDao() = runTest(mainRule.testDispatcher) {
        assertTrue(repo.pagingSearch("   ").asSnapshot().isEmpty())

        verify(exactly = 0) { dao.pagingSourceSearchFts(any()) }
    }

    @Test
    fun contentExists_reflectsDaoCount() = runTest(mainRule.testDispatcher) {
        coEvery { dao.countByContent("x") } returns 1
        coEvery { dao.countByContent("y") } returns 0

        assertTrue(repo.contentExists("x"))
        assertFalse(repo.contentExists("y"))
    }

    // ── error wrapping ───────────────────────────────────────────────

    @Test
    fun dbFailure_wrapsSqlException() = runTest(mainRule.testDispatcher) {
        val sqlError = mockk<android.database.SQLException>()
        coEvery { dao.countByContent(any()) } throws sqlError

        try {
            repo.saveIfNew("boom")
            fail("expected ClipboardDbException")
        } catch (e: ClipboardDbException) {
            assertEquals(sqlError, e.cause)
        }
    }

    // ── file-backed maintenance on real temp files ───────────────────

    private fun imagesDir(): File = File(tmp.root, "images").also { it.mkdirs() }

    @Test
    fun cleanupOrphanedImages_deletesOnlyOldUnreferencedFiles() = runTest(mainRule.testDispatcher) {
        val dir = imagesDir()
        val orphanOld = File(dir, "clip_1.png").apply {
            writeBytes(ByteArray(10)); setLastModified(System.currentTimeMillis() - 10_000)
        }
        val orphanYoung = File(dir, "clip_2.png").apply {
            writeBytes(ByteArray(10)); setLastModified(System.currentTimeMillis())
        }
        val referenced = File(dir, "clip_3.png").apply {
            writeBytes(ByteArray(10)); setLastModified(System.currentTimeMillis() - 10_000)
        }
        coEvery { dao.getAllImagePaths() } returns listOf(referenced.absolutePath)

        repo.cleanupOrphanedImages(tmp.root, minAgeMs = 5_000)

        assertFalse(orphanOld.exists())
        assertTrue(orphanYoung.exists())
        assertTrue(referenced.exists())
    }

    @Test
    fun enforceImageQuota_stopsOnceUnderQuota() = runTest(mainRule.testDispatcher) {
        val dir = imagesDir()
        val now = System.currentTimeMillis()
        val oldest = File(dir, "clip_1.png").apply {
            writeBytes(ByteArray(100)); setLastModified(now - 3000)
        }
        val middle = File(dir, "clip_2.png").apply {
            writeBytes(ByteArray(100)); setLastModified(now - 2000)
        }
        val newest = File(dir, "clip_3.png").apply {
            writeBytes(ByteArray(100)); setLastModified(now - 1000)
        }
        coEvery { dao.getPinnedImagePaths() } returns emptyList()
        coEvery { dao.clearImageUri(any()) } returns 1
        every { settings.maxImageBytes } returns MutableStateFlow(150)

        repo.enforceImageQuota(tmp.root)

        // 300 bytes total, quota 150: evict two oldest, then stop.
        assertFalse(oldest.exists())
        assertFalse(middle.exists())
        assertTrue(newest.exists())
        coVerify(exactly = 2) { dao.clearImageUri(any()) }
    }

    @Test
    fun enforceImageQuota_neverDeletesPinned() = runTest(mainRule.testDispatcher) {
        val dir = imagesDir()
        val plain = File(dir, "clip_1.png").apply { writeBytes(ByteArray(100)) }
        val pinned = File(dir, "clip_2.png").apply { writeBytes(ByteArray(100)) }
        coEvery { dao.getPinnedImagePaths() } returns listOf(pinned.absolutePath)
        coEvery { dao.clearImageUri(any()) } returns 1
        every { settings.maxImageBytes } returns MutableStateFlow(50)

        repo.enforceImageQuota(tmp.root)

        assertFalse(plain.exists())
        assertTrue(pinned.exists())
    }

    @Test
    fun relocateLegacyImages_movesReferencedFile() = runTest(mainRule.testDispatcher) {
        val legacy = File(tmp.root, "clip_legacy.png").apply { writeBytes(ByteArray(5)) }
        coJustRun { dao.updateImagePath(any(), any()) }

        repo.relocateLegacyImages(tmp.root)

        assertFalse(legacy.exists())
        assertTrue(File(File(tmp.root, "images"), "clip_legacy.png").exists())
        coVerify { dao.updateImagePath(legacy.absolutePath, any()) }
    }

    @Test
    fun mergeDuplicateGroup_mergesAndReturnsDeletedCount() = runTest(mainRule.testDispatcher) {
        val keeper = clipEntity(id = 1, content = "same", notes = null)
        val dupes = listOf(
            clipEntity(id = 2, content = "same", notes = "keep me"),
            clipEntity(id = 3, content = "same")
        )
        coEvery { dao.findDuplicatesExcluding(1, "same") } returns dupes
        coEvery { dao.getById(1) } returns keeper
        coJustRun { dao.mergeDuplicateGroup(1, listOf(2, 3), "keep me") }

        assertEquals(2, repo.mergeDuplicateGroup(1, "same"))
    }

    // ── cancellation ─────────────────────────────────────────────────

    @Test
    fun cancelledCollection_completesWithoutLeak() = runTest(mainRule.testDispatcher) {
        every { dao.observePinned() } returns flow {
            emit(emptyList())
            awaitCancellation()
        }

        val job = launch(mainRule.testDispatcher) {
            repo.observePinned().collect { }
        }
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
    }
}
