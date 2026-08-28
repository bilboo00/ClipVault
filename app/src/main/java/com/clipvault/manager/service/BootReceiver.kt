package com.clipvault.manager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipvault.manager.data.preferences.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Read the real persisted value, not the StateFlow's initial
                // value (which is `true` regardless of the DataStore setting).
                val enabled = settings.observeMonitoringEnabledRaw()
                if (enabled) ClipboardMonitorService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}