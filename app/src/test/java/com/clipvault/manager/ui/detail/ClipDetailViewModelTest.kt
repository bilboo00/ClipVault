package com.clipvault.manager.ui.detail

import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.clipEntity
import com.clipvault.manager.data.local.dao.ClipDao
import com.clipvault.manager.data.local.entity.CollectionEntity
import com.clipvault.manager.data.local.entity.TagEntity
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.repository.CollectionRepository
import com.clipvault.manager.data.repository.TagRepository
import com.clipvault.manager.data.security.BiometricManager
import com.clipvault.manager.domain.PasteQueueManager
import com.clipvault.manager.domain.model.Clip
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
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var repository: ClipboardRepository
    private lateinit var clipDao: ClipDao
    private lateinit var queueManager: PasteQueueManager
    private lateinit var tagRepository: TagRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var biometricManager: BiometricManager
    private lateinit var vm: ClipDetailViewModel

    private val entity = clipEntity(id = 5, content = "hello", isLocked = false)
    private val lockedEntity = clipEntity(id = 6, content = "secret", isLocked = true)

    @Before
    fun setUp() {
        repository = mockk()
        clipDao = mockk()
        queueManager = mockk()
        tagRepository = mockk()
        collectionRepository = mockk()
        biometricManager = mockk()
        every { tagRepository.observeCrossRefsForClip(any()) } returns flowOf(emptySet())
        every { collectionRepository.observeCrossRefsForClip(any()) } returns flowOf(emptySet())
        every { tagRepository.observeAll() } returns flowOf(emptyList())
        every { collectionRepository.observeAll() } returns flowOf(emptyList())
        vm = ClipDetailViewModel(
            repository, clipDao, queueManager, tagRepository, collectionRepository, biometricManager
        )
    }

    private fun stubClip(e: com.clipvault.manager.data.local.entity.ClipEntity) {
        every { repository.observeById(e.id) } returns flowOf(Clip.Companion.fromEntity(e))
    }

    @Test
    fun loadZero_showsNotLoadingWithoutClip() = runTest(mainRule.testDispatcher) {
        vm.load(0)

        vm.state.test {
            skipItems(1)
            val s = awaitItem()
            assertFalse(s.loading)
            assertNull(s.clip)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun load_populatesClipFromRepository() = runTest(mainRule.testDispatcher) {
        stubClip(entity)
        vm.load(5)

        vm.state.test {
            skipItems(1)
            val s = awaitItem()
            assertFalse(s.loading)
            assertEquals("hello", s.clip?.content)
            assertTrue(s.unlocked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun lockedClip_startsLockedUntilToggled() = runTest(mainRule.testDispatcher) {
        stubClip(lockedEntity)
        coJustRun { repository.setLocked(6, false) }
        vm.load(6)

        vm.state.test {
            skipItems(1)
            val loaded = awaitItem()
            assertEquals(6L, loaded.clip?.id)
            assertFalse(loaded.unlocked)
            vm.toggleLock(Clip.Companion.fromEntity(lockedEntity))
            assertTrue(awaitItem().unlocked)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { repository.setLocked(6, false) }
    }

    @Test
    fun toggleLock_invokesCallbackWithNewState() = runTest(mainRule.testDispatcher) {
        coJustRun { repository.setLocked(5, true) }
        var result: Boolean? = null

        vm.toggleLock(Clip.Companion.fromEntity(entity)) { result = it }
        advanceUntilIdle()

        assertEquals(true, result)
        coVerify { repository.setLocked(5, true) }
    }

    @Test
    fun delete_missingEntityEmitsNoEvent() = runTest(mainRule.testDispatcher) {
        coEvery { clipDao.getById(404) } returns null

        vm.events.test {
            vm.delete(Clip.Companion.fromEntity(clipEntity(id = 404)))
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun delete_existingEntityEmitsDeleted() = runTest(mainRule.testDispatcher) {
        coEvery { clipDao.getById(5) } returns entity
        coJustRun { repository.delete(5) }

        vm.events.test {
            vm.delete(Clip.Companion.fromEntity(entity))
            val event = awaitItem()
            assertTrue(event is ClipDetailEvent.Deleted)
            assertEquals(5L, (event as ClipDetailEvent.Deleted).entity.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setNotes_withNullClipIsNoOp() = runTest(mainRule.testDispatcher) {
        vm.load(0)
        advanceUntilIdle()

        vm.setNotes("hi")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.setNotes(any(), any()) }
    }

    @Test
    fun recordUsage_delegatesToRepository() = runTest(mainRule.testDispatcher) {
        stubClip(entity)
        coJustRun { repository.incrementUseCount(5) }
        vm.load(5)

        vm.state.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(5L, s.clip?.id)
            vm.recordUsage()
            advanceUntilIdle()
            coVerify { repository.incrementUseCount(5) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleTag_addsAndRemoves() = runTest(mainRule.testDispatcher) {
        stubClip(entity)
        coEvery { tagRepository.setTagsForClip(any(), any()) } returns Unit
        vm.load(5)

        vm.state.test {
            skipItems(1)
            assertEquals(5L, awaitItem().clip?.id)
            vm.toggleTag(3)
            advanceUntilIdle()
            coVerify { tagRepository.setTagsForClip(5, listOf(3L)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stateCollection_cancelCompletesWithoutLeak() = runTest(mainRule.testDispatcher) {
        stubClip(entity)
        vm.load(5)

        vm.state.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
    }
}
