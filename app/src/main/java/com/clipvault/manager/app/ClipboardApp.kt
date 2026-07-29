package com.clipvault.manager.app

import android.app.Application
import com.clipvault.manager.data.preferences.SettingsManager
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
    }
}