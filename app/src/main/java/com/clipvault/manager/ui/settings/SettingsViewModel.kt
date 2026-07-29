package com.clipvault.manager.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.service.BubbleService
import com.clipvault.manager.service.ClipboardMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val monitoringEnabled: Boolean = true,
    val retentionDays: Int = 30,
    val themeMode: Int = 0,
    val bubbleEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsManager,
    private val repository: ClipboardRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.monitoringEnabled,
        settings.maxHistoryDays,
        settings.darkThemeOverride,
        settings.bubbleEnabled
    ) { enabled, days, theme, bubble ->
        SettingsUiState(enabled, days, theme, bubble)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val onboardingDone: Flow<Boolean> = settings.onboardingCompleted

    fun setMonitoring(enabled: Boolean) = viewModelScope.launch {
        try {
            settings.setMonitoring(enabled)
            if (enabled) ClipboardMonitorService.start(context)
            else ClipboardMonitorService.stop(context)
        } catch (e: Exception) {
            Log.e(TAG, "setMonitoring($enabled) failed", e)
        }
    }

    fun setRetention(days: Int) = viewModelScope.launch {
        try {
            settings.setRetentionDays(days)
            if (days > 0) {
                val cutoff = System.currentTimeMillis() - days * 86_400_000L
                // Prune on IO dispatcher to keep UI responsive even on large DBs
                withContext(Dispatchers.IO) {
                    try {
                        repository.pruneOlderThan(cutoff)
                    } catch (e: Exception) {
                        Log.e(TAG, "pruneOlderThan($cutoff) failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setRetention($days) failed", e)
        }
    }

    fun setTheme(mode: Int) = viewModelScope.launch {
        try {
            settings.setTheme(mode)
        } catch (e: Exception) {
            Log.e(TAG, "setTheme($mode) failed", e)
        }
    }

    fun setBubbleEnabled(enabled: Boolean) = viewModelScope.launch {
        try {
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    return@launch
                }
                settings.setBubbleEnabled(true)
                BubbleService.start(context)
            } else {
                settings.setBubbleEnabled(false)
                BubbleService.stop(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "setBubbleEnabled($enabled) failed", e)
        }
    }

    fun resetOnboarding() = viewModelScope.launch {
        try { settings.setOnboardingCompleted(false) } catch (e: Exception) {
            Log.e(TAG, "resetOnboarding failed", e)
        }
    }

    fun deleteAll() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { repository.deleteAll() }
        } catch (e: Exception) {
            Log.e(TAG, "deleteAll failed", e)
        }
    }

    suspend fun exportAllClips(): List<ClipEntity> =
        withContext(Dispatchers.IO) { repository.getAllEntities() }

    suspend fun importClips(clips: List<ClipEntity>): Int = withContext(Dispatchers.IO) {
        var count = 0
        clips.forEach { entity ->
            val id = repository.insertForImport(entity)
            if (id != -1L) count++
        }
        count
    }

    fun overlayPermissionIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}