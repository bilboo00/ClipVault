package com.clipvault.manager.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clipvault.manager.data.local.dao.DuplicateGroup
import com.clipvault.manager.data.local.entity.ClipEntity
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.service.BubbleService
import com.clipvault.manager.service.ClipboardMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    val bubbleEnabled: Boolean = false,
    val maskSensitiveContent: Boolean = false,
    val requireBiometric: Boolean = false
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
        settings.bubbleEnabled,
        settings.maskSensitiveContent,
        settings.requireBiometric
    ) { values ->
        SettingsUiState(
            monitoringEnabled = values[0] as Boolean,
            retentionDays = values[1] as Int,
            themeMode = values[2] as Int,
            bubbleEnabled = values[3] as Boolean,
            maskSensitiveContent = values[4] as Boolean,
            requireBiometric = values[5] as Boolean
        )
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
                if (!Settings.canDrawOverlays(context)) {
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

    fun setMaskSensitiveContent(enabled: Boolean) = viewModelScope.launch {
        try { settings.setMaskSensitiveContent(enabled) } catch (e: Exception) {
            Log.e(TAG, "setMaskSensitiveContent($enabled) failed", e)
        }
    }

    fun setRequireBiometric(enabled: Boolean) = viewModelScope.launch {
        try { settings.setRequireBiometric(enabled) } catch (e: Exception) {
            Log.e(TAG, "setRequireBiometric($enabled) failed", e)
        }
    }

    fun deleteAll() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { repository.deleteAllUnpinned() }
        } catch (e: Exception) {
            Log.e(TAG, "deleteAll failed", e)
        }
    }

    // ── Duplicate detection ─────────────────────────────────────────

    private val _duplicates = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicates: StateFlow<List<DuplicateGroup>> = _duplicates

    fun refreshDuplicates() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                _duplicates.value = repository.findDuplicateGroups()
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshDuplicates failed", e)
        }
    }

    fun mergeDuplicate(keepId: Long, content: String) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                repository.mergeDuplicateGroup(keepId, content)
            }
            refreshDuplicates()
        } catch (e: Exception) {
            Log.e(TAG, "mergeDuplicate failed", e)
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
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}