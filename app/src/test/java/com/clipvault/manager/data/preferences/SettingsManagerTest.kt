package com.clipvault.manager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var file: File
    private lateinit var manager: SettingsManager

    private fun freshManager(): SettingsManager {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val ds: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        return object : SettingsManager(mockk<Context>()) {
            override fun openDataStore(): DataStore<Preferences> = ds
        }
    }

    @Before
    fun setUp() {
        file = File(tmp.root, "test_settings.preferences_pb")
        manager = freshManager()
    }

    @Test
    fun defaults_areSaneOnFreshStore() = runTest(mainRule.testDispatcher) {
        assertTrue(manager.observeMonitoringEnabledRaw())
        assertEquals(30, manager.maxHistoryDays.first())
    }

    @Test
    fun setMonitoring_persistsAndEmits() = runTest(mainRule.testDispatcher) {
        manager.setMonitoring(false)

        assertFalse(manager.observeMonitoringEnabledRaw())
        manager.monitoringEnabled.test {
            skipItems(1)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setRetentionDays_persists() = runTest(mainRule.testDispatcher) {
        manager.setRetentionDays(7)

        manager.maxHistoryDays.test {
            skipItems(1)
            assertEquals(7, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addRecentSearch_ordersMostRecentFirstAndCaps() = runTest(mainRule.testDispatcher) {
        manager.addRecentSearch("alpha")
        manager.addRecentSearch("beta")
        assertEquals(
            listOf("beta", "alpha"),
            manager.recentSearches.first { it.size == 2 }
        )
        repeat(10) { manager.addRecentSearch("q$it") }
        val recents = manager.recentSearches.first { it.size == 8 }
        assertEquals("q9", recents.first())
    }

    @Test
    fun addRecentSearch_dedupesCaseInsensitively() = runTest(mainRule.testDispatcher) {
        manager.addRecentSearch("alpha")
        manager.addRecentSearch("ALPHA")

        assertEquals(listOf("ALPHA"), manager.recentSearches.first { it.size == 1 })
    }

    @Test
    fun addRecentSearch_blankIsIgnored() = runTest(mainRule.testDispatcher) {
        manager.recentSearches.test {
            skipItems(1)
            manager.addRecentSearch("   ")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addRecentSearch_separatorCharDoesNotSplitEntry() = runTest(mainRule.testDispatcher) {
        manager.addRecentSearch("a\u001Fb")

        manager.recentSearches.test {
            skipItems(1)
            assertEquals(listOf("a b"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearRecentSearches_emptiesList() = runTest(mainRule.testDispatcher) {
        manager.addRecentSearch("alpha")
        assertEquals(listOf("alpha"), manager.recentSearches.first { it.isNotEmpty() })

        manager.clearRecentSearches()

        assertEquals(emptyList<String>(), manager.recentSearches.first { it.isEmpty() })
    }

    @Test
    fun cancelledCollection_completesWithoutLeak() = runTest(mainRule.testDispatcher) {
        manager.monitoringEnabled.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
    }
}
