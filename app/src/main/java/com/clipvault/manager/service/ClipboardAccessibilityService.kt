package com.clipvault.manager.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.data.repository.ClipboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardAccessibilityService : AccessibilityService() {

    @Inject lateinit var repository: ClipboardRepository
    @Inject lateinit var settings: SettingsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSeen: String? = null
    private var lastSeenAt: Long = 0L
    private var lastVerifyAt: Long = 0L
    private val monitoringEnabledCached = AtomicBoolean(true)

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 200
                flags = flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
        } catch (e: Exception) {
            android.util.Log.w("AccessibilityService", "Failed to configure serviceInfo", e)
        }
        // Periodically refresh the monitoring-enabled flag so we don't read
        // DataStore on every accessibility event (which can fire many times
        // per second). SettingsManager.monitoringEnabled is a Flow<Boolean>
        // (not StateFlow) so caching via AtomicBoolean is the lightest path.
        scope.launch {
            while (isActive) {
                runCatching { monitoringEnabledCached.set(settings.monitoringEnabled.first()) }
                delay(MONITORING_REFRESH_MS)
            }
        }
    }

    @android.annotation.SuppressLint("SwitchIntDef")
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            // Only VIEW_CLICKED and VIEW_LONG_CLICKED are subscribed (see
            // onServiceConnected). Any other type is ignored.
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    val text = event.text?.joinToString(" ")?.lowercase().orEmpty()
                    if (text.contains("copy") || text.contains("paste") ||
                        text.contains("cut") || text.contains("select all")) {
                        attemptRead(reason = "copy-action")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AccessibilityService", "Event handling failed", e)
        }
    }

    private fun attemptRead(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastSeenAt < READ_DEBOUNCE_MS) return
        lastSeenAt = now

        scope.launch {
            try {
                if (!monitoringEnabledCached.get()) return@launch
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData? = cm.primaryClip
                if (clip == null || clip.itemCount == 0) return@launch
                val text = (clip.getItemAt(0).coerceToText(this@ClipboardAccessibilityService)
                    ?.toString() ?: "").trim()
                if (text.isBlank()) return@launch
                if (text != lastSeen) {
                    // New clipboard value — delete-suppressions for the previous
                    // content no longer apply.
                    repository.clearDeleteSuppressions()
                }
                if (text == lastSeen) {
                    // Same content as before — never re-add content the user
                    // explicitly deleted while it's still on the clipboard;
                    // otherwise still check it wasn't deleted from history
                    // (delete → re-copy must re-save), but throttle the DB query
                    // so frequent events don't hammer Room.
                    if (repository.isContentSuppressed(text)) return@launch
                    val now = System.currentTimeMillis()
                    if (now - lastVerifyAt < REVERIFY_INTERVAL_MS) return@launch
                    lastVerifyAt = now
                    if (repository.contentExists(text)) return@launch
                }
                // Advance lastSeen even when saveIfNew dedupes, so click events
                // don't re-query the DB for already-known content.
                lastSeen = text
                repository.saveIfNew(text, sourceLabel = reason)
            } catch (_: SecurityException) {
                // Clipboard locked by another app
            } catch (_: Exception) {
                // Defensive
            }
        }
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val READ_DEBOUNCE_MS = 400L
        private const val REVERIFY_INTERVAL_MS = 30_000L
        private const val MONITORING_REFRESH_MS = 5_000L
    }
}