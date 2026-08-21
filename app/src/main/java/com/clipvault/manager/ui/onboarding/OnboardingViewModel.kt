package com.clipvault.manager.ui.onboarding

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.service.BubbleService
import com.clipvault.manager.service.ClipboardAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val currentPage: Int = 0,
    val totalPages: Int = 4,
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val monitoringOn: Boolean = true
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.monitoringEnabled.first().let { enabled ->
                _state.update { it.copy(monitoringOn = enabled) }
            }
        }
        // Poll permission state so the UI updates when user returns from Settings.
        // 2 s cadence (was 800 ms) cuts binder round-trips; skip ticks on pages
        // that don't show permission state (Welcome / BackgroundService).
        viewModelScope.launch {
            while (isActive) {
                if (_state.value.currentPage >= PERMISSION_PAGE_START) {
                    refreshPermissions()
                }
                delay(PERMISSION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshPermissions() {
        _state.update {
            it.copy(
                accessibilityGranted = isAccessibilityEnabled(),
                overlayGranted = isOverlayGranted()
            )
        }
    }

    fun next() {
        _state.update { current ->
            if (current.currentPage < current.totalPages - 1)
                current.copy(currentPage = current.currentPage + 1)
            else current
        }
    }

    fun previous() {
        _state.update { current ->
            if (current.currentPage > 0)
                current.copy(currentPage = current.currentPage - 1)
            else current
        }
    }

    fun finish() = viewModelScope.launch {
        settings.setOnboardingCompleted(true)
        // If user enabled the bubble during onboarding, start it now
        if (_state.value.overlayGranted) {
            settings.setBubbleEnabled(true)
            BubbleService.start(context)
        }
    }

    fun enableAccessibility() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun enableOverlay() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = context.packageName + "/" + ClipboardAccessibilityService::class.java.name
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.id == expected }
    }

    private fun isOverlayGranted(): Boolean =
        Settings.canDrawOverlays(context)

    companion object {
        /** Pages 0 (Welcome) and 1 (BackgroundService) don't show permission state. */
        private const val PERMISSION_PAGE_START = 2

        /** Polling cadence for accessibility/overlay state. */
        private const val PERMISSION_POLL_INTERVAL_MS = 2_000L
    }
}