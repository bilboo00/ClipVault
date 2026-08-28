package com.clipvault.manager.app

import android.app.Application
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.service.ClipboardMonitorService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClipboardApp : Application() {

    @Inject lateinit var settings: SettingsManager
    @Inject lateinit var repository: ClipboardRepository

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            android.util.Log.w("ClipboardApp", "appScope failure", e)
        }
    )

    override fun onCreate() {
        super.onCreate()
        // Install before anything else can throw — writes exact stack traces
        // to filesDir/crash_reports so crashes can be copied out of Settings
        // without needing adb attached.
        com.clipvault.manager.util.CrashReporter.install(this)
        appScope.launch {
            try {
                // Use the dataStore flow (not the StateFlow's `first()`, which
                // returns the initial value without ever reading DataStore) so
                // the actual persisted preference — `false` when the user has
                // disabled monitoring — is honoured here. Otherwise the service
                // would be force-started even when the user opted out.
                val enabled = settings.observeMonitoringEnabledRaw()
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
                // Move legacy images into the images/ subdir (matches the
                // narrowed FileProvider paths) before orphan sweeping.
                repository.relocateLegacyImages(filesDir)
                repository.cleanupOrphanedImages(filesDir, IMAGE_GRACE_MS)
                val retentionDays = settings.maxHistoryDays.first()
                if (retentionDays > 0) {
                    repository.pruneOlderThan(System.currentTimeMillis() - retentionDays * 86_400_000L)
                }
            } catch (_: Exception) {
                // Best-effort maintenance — never crash the app
            }
        }
        // The process lives for days behind the foreground service, so
        // launch-only pruning left expired clips visible until a restart.
        appScope.launch {
            try {
                while (true) {
                    delay(PRUNE_INTERVAL_MS)
                    repository.pruneExpired()
                    repository.pruneExhausted()
                    repository.cleanupOrphanedRefs()
                }
            } catch (_: Exception) {
                // Best-effort maintenance — never crash the app
            }
        }
        appScope.launch {
            try {
                // Periodic sweep of image files whose clip rows are gone.
                while (true) {
                    delay(IMAGE_SWEEP_INTERVAL_MS)
                    repository.cleanupOrphanedImages(filesDir, IMAGE_GRACE_MS)
                }
            } catch (_: Exception) {
                // Best-effort maintenance — never crash the app
            }
        }
    }

    companion object {
        private const val PRUNE_INTERVAL_MS = 5 * 60_000L
        private const val IMAGE_SWEEP_INTERVAL_MS = 24 * 60 * 60_000L
        /** Keep files this young so an undo right after delete still works. */
        private const val IMAGE_GRACE_MS = 24 * 60 * 60_000L
    }
}
