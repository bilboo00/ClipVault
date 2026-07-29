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
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardAccessibilityService : AccessibilityService() {

    @Inject lateinit var repository: ClipboardRepository
    @Inject lateinit var settings: SettingsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSeen: String? = null
    private var lastSeenAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    val text = event.text?.joinToString(" ")?.lowercase().orEmpty()
                    if (text.contains("copy") || text.contains("paste") ||
                        text.contains("cut") || text.contains("select all")) {
                        attemptRead(reason = "copy-action")
                    }
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    val cls = event.className?.toString().orEmpty()
                    if (cls.contains("EditText") || cls.contains("TextView")) {
                        attemptRead(reason = "text-focused")
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    attemptRead(reason = "window-change")
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
                val enabled = settings.monitoringEnabled.first()
                if (!enabled) return@launch
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData? = cm.primaryClip
                if (clip == null || clip.itemCount == 0) return@launch
                val text = clip.getItemAt(0).coerceToText(this@ClipboardAccessibilityService)
                    ?.toString().orEmpty()
                if (text.isBlank() || text == lastSeen) return@launch
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
    }
}