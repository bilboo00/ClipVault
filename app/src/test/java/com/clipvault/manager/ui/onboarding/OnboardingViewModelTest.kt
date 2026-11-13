package com.clipvault.manager.ui.onboarding

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.MainDispatcherRule
import com.clipvault.manager.data.preferences.SettingsManager
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var settings: SettingsManager
    private lateinit var vm: OnboardingViewModel

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        settings = mockk()
        coEvery { settings.observeMonitoringEnabledRaw() } returns false
        coJustRun { settings.setOnboardingCompleted(any()) }
        coJustRun { settings.setBubbleEnabled(any()) }
        every { context.packageName } returns "com.clipvault.manager"
        every { context.getSystemService(any<String>()) } returns null
        vm = OnboardingViewModel(context, settings)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
    }

    @Test
    fun next_clampsAtLastPage() {
        repeat(10) { vm.next() }
        assertEquals(3, vm.state.value.currentPage)
    }

    @Test
    fun previous_clampsAtFirstPage() {
        vm.next()
        vm.previous()
        vm.previous()
        assertEquals(0, vm.state.value.currentPage)
    }

    @Test
    fun next_advancesOnePageAtATime() {
        vm.next()
        assertEquals(1, vm.state.value.currentPage)
        vm.next()
        assertEquals(2, vm.state.value.currentPage)
    }
}
