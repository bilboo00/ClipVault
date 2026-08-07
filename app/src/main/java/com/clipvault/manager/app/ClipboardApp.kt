package com.clipvault.manager.app

import android.app.Application
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.service.ClipboardMonitorService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClipboardApp : Application() {

    @Inject lateinit var settings: SettingsManager
    @Inject lateinit var repository: ClipboardRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                val enabled = settings.monitoringEnabled.first()
                if (enabled) ClipboardMonitorService.start(this@ClipboardApp)
            } catch (_: Exception) {
                // Never let DataStore / service-start failures crash the app
            }
        }
        appScope.launch {
            try {
                // Enforce temporary-clip and retention policies on every launch.
                repository.pruneExpired()
                repository.pruneExhausted()
                val retentionDays = settings.maxHistoryDays.first()
                if (retentionDays > 0) {
                    repository.pruneOlderThan(System.currentTimeMillis() - retentionDays * 86_400_000L)
                }
            } catch (_: Exception) {
                // Best-effort maintenance — never crash the app
            }
        }
    }
}
