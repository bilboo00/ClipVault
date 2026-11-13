package com.clipvault.manager.ui.settings

import android.content.Context
import app.cash.turbine.test
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.clipvault.manager.data.security.BiometricManager
import io.mockk.just
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var settings: SettingsManager
    private lateinit var repository: ClipboardRepository
    private lateinit var context: Context
    private lateinit var biometricManager: BiometricManager
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        settings = mockk()
        repository = mockk()
        context = mockk()
        biometricManager = mockk(relaxed = true)
        every { settings.monitoringEnabled } returns MutableStateFlow(true)
        every { settings.bubbleEnabled } returns MutableStateFlow(false)
        every { settings.requireBiometric } returns MutableStateFlow(false)
        every { settings.maxHistoryDays } returns MutableStateFlow(30)
        every { settings.darkThemeOverride } returns MutableStateFlow(0)
        every { settings.maskSensitiveContent } returns MutableStateFlow(false)
        every { settings.onboardingCompleted } returns MutableStateFlow(false)
        every { settings.dynamicColors } returns MutableStateFlow(false)
        every { settings.palette } returns MutableStateFlow(0)
        every { context.getSystemService(any<String>()) } returns null
        every { context.stopService(any()) } returns false
        vm = SettingsViewModel(settings, repository, biometricManager, context)
    }

    @Test
    fun state_combinesAllPreferenceFlows() = runTest(mainRule.testDispatcher) {
        vm.state.test {
            val s = awaitItem()
            assertEquals(true, s.monitoringEnabled)
            assertEquals(30, s.retentionDays)
            assertEquals(0, s.themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setMonitoringFalse_persistsAndStopsService() = runTest(mainRule.testDispatcher) {
        coJustRun { settings.setMonitoring(false) }

        vm.setMonitoring(false)
        advanceUntilIdle()

        coVerify { settings.setMonitoring(false) }
    }

    @Test
    fun setRetentionZero_skipsPrune() = runTest(mainRule.testDispatcher) {
        coJustRun { settings.setRetentionDays(0) }

        vm.setRetention(0)
        advanceUntilIdle()

        coVerify { settings.setRetentionDays(0) }
        coVerify(exactly = 0) { repository.pruneOlderThan(any()) }
    }

    @Test
    fun setRetentionPositive_prunesOldClips() = runTest(mainRule.testDispatcher) {
        coJustRun { settings.setRetentionDays(30) }
        coJustRun { repository.pruneOlderThan(any()) }

        vm.setRetention(30)
        advanceUntilIdle()

        coVerify { repository.pruneOlderThan(any()) }
    }

    @Test
    fun setBubbleEnabled_withoutOverlayPermission_returnsEarly() = runTest(mainRule.testDispatcher) {
        // Settings.canDrawOverlays is an Android static returning false on JVM stubs.
        vm.setBubbleEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { settings.setBubbleEnabled(any()) }
    }

    @Test
    fun setTheme_persistsMode() = runTest(mainRule.testDispatcher) {
        coJustRun { settings.setTheme(2) }

        vm.setTheme(2)
        advanceUntilIdle()

        coVerify { settings.setTheme(2) }
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
