package com.clipvault.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo

import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipvault.manager.app.MainActivity
import com.clipvault.manager.R
import com.clipvault.manager.service.startForegroundCompat
import com.clipvault.manager.data.repository.ClipboardRepository
import com.clipvault.manager.data.preferences.SettingsManager
import com.clipvault.manager.util.ImageCopier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardMonitorService : Service() {

    @Inject lateinit var repository: ClipboardRepository
    @Inject lateinit var settingsManager: SettingsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var lastSeen: String? = null
    private var lastImageUri: String? = null
    private var lastImagePath: String? = null
    private var lastVerifyAt: Long = 0L
    private lateinit var clipboardManager: ClipboardManager
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    @Volatile private var maskContent = false
    @Volatile private var screenOff = false
    @Volatile private var lastPreview: String? = null
    private var screenReceiver: BroadcastReceiver? = null

    @android.annotation.SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        // Cache the masking setting and keep it updated. Mask whenever sensitive
        // content masking OR the biometric app lock is enabled — a biometric
        // lock must never leak clip previews through the ongoing notification.
        scope.launch {
            combine(
                settingsManager.maskSensitiveContent,
                settingsManager.requireBiometric
            ) { mask, biometric -> mask || biometric }
                .collect { maskContent = it }
        }
        registerScreenReceiver()
        createChannel()
        startForegroundCompat(NOTIFICATION_ID, buildNotification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                stopListening()
                updateNotification(paused = true)
            }
            ACTION_RESUME -> {
                startListening()
                updateNotification(paused = false)
            }
        }
        return START_STICKY
    }

    /**
     * Subscribe to system clipboard-change events.
     *
     * On Android 10+ the callback only fires for clips the system deems
     * accessible to a foreground service — typically when the source app
     * is the user's foreground task and the clipboard is non-sensitive.
     * We additionally poll on a long interval as a safety net for
     * emulators / older devices where the listener may not fire reliably.
     */
    private fun startListening() {
        if (listener != null) return
        // Snapshot the current clipboard WITHOUT saving — this primes `lastSeen`
        // so the first poll/listener event doesn't re-save the stale clipboard.
        // Important: do this even if SecurityException is thrown, so the polling
        // loop has a real baseline to compare against.
        primeLastSeenWithRetries()
        listener = ClipboardManager.OnPrimaryClipChangedListener { pollClipboardSafely() }
            .also { clipboardManager.addPrimaryClipChangedListener(it) }
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    pollClipboardSafely()
                }
            }
        }
    }

    /**
     * Try to read the clipboard and set [lastSeen] to its current content.
     * Retries with increasing backoff because on Android 10+ the very first
     * primaryClip access from a freshly-started foreground service can throw
     * SecurityException — we must succeed before the polling loop kicks in,
     * otherwise the first poll would treat the stale clipboard as a new copy.
     */
    private fun primeLastSeenWithRetries() {
        scope.launch {
            val backoffs = longArrayOf(0L, 100L, 300L, 700L)
            for (delayMs in backoffs) {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                val captured = try {
                    val clip = clipboardManager.primaryClip
                    if (clip == null || clip.itemCount == 0) return@launch
                    val item = clip.getItemAt(0)
                    (item.text?.toString()?.takeIf { it.isNotEmpty() }
                        ?: item.coerceToText(this@ClipboardMonitorService)?.toString())
                } catch (_: SecurityException) {
                    null
                } catch (_: Exception) {
                    null
                }
                if (!captured.isNullOrEmpty()) {
                    lastSeen = captured
                    return@launch
                }
            }
            // Couldn't read clipboard in time — leave lastSeen as-is to avoid
            // a false-positive save on the next poll.
        }
    }

    private fun stopListening() {
        listener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        listener = null
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollClipboardSafely() {
        scope.launch {
            try {
                val clip: ClipData? = clipboardManager.primaryClip
                if (clip == null || clip.itemCount == 0) return@launch
                val item = clip.getItemAt(0)

                val imageUri = item.uri?.takeIf { uri ->
                    val type = contentResolver.getType(uri)
                    type != null && type.startsWith("image/")
                }

                if (imageUri != null) {
                    val uriKey = item.uri.toString()
                    val now = System.currentTimeMillis()
                    // Same source URI as the last capture — skip unless the clip
                    // was deleted from history (delete → re-copy must re-save),
                    // but never re-add an image the user explicitly deleted.
                    // DB checks are throttled so the poll loop doesn't hammer Room.
                    if (uriKey == lastImageUri) {
                        val path = lastImagePath
                        if (path != null) {
                            if (repository.isImageSuppressed(path)) return@launch
                            if (now - lastVerifyAt < REVERIFY_INTERVAL_MS) return@launch
                            lastVerifyAt = now
                            if (repository.imageExists(path)) return@launch
                        }
                    } else {
                        repository.clearDeleteSuppressions()
                    }
                    val savedPath = ImageCopier.copyToInternalStorage(this@ClipboardMonitorService, imageUri)
                    if (savedPath != null) {
                        val saved = repository.saveImage(savedPath, sourceLabel = null)
                        if (saved != null) {
                            lastImageUri = uriKey
                            lastImagePath = savedPath
                            lastSeen = "[Image]"
                            updateNotification("Image saved")
                        }
                    }
                    return@launch
                }

                val text = withContext(Dispatchers.Default) {
                    item.text?.toString()?.takeIf { it.isNotEmpty() }
                        ?: item.coerceToText(this@ClipboardMonitorService)?.toString()
                }.orEmpty()
                if (text.isBlank()) return@launch
                if (text != lastSeen) {
                    // New clipboard value — delete-suppressions for the previous
                    // content no longer apply.
                    repository.clearDeleteSuppressions()
                }
                if (text == lastSeen) {
                    // Same content as the last capture — never re-add content the
                    // user explicitly deleted while it's still on the clipboard;
                    // otherwise still verify it wasn't deleted from history, but
                    // throttle the DB query so the 800ms poll doesn't fire
                    // ~108k COUNTs/day.
                    if (repository.isContentSuppressed(text)) return@launch
                    val now = System.currentTimeMillis()
                    if (now - lastVerifyAt < REVERIFY_INTERVAL_MS) return@launch
                    lastVerifyAt = now
                    if (repository.contentExists(text)) return@launch
                }
                val saved = repository.saveIfNew(text, sourceLabel = null)
                // Advance lastSeen even when the save was deduped, so we don't
                // re-query the DB on every poll for an already-known clip.
                lastSeen = text
                if (saved != null) {
                    updateNotification(text)
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    private fun buildNotification(preview: String?, paused: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ClipboardMonitorService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIcon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleLabel = if (paused) getString(R.string.action_resume) else getString(R.string.action_pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(
                if ((maskContent || screenOff) && preview != null) getString(R.string.notif_idle)
                else preview?.take(80) ?: getString(R.string.notif_idle)
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(toggleIcon, toggleLabel, toggleIntent)
            .build()
    }

    private fun updateNotification(preview: String? = null, paused: Boolean = false) {
        if (preview != null) lastPreview = preview
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(preview, paused))
        } catch (_: Exception) { /* never crash from notif update */ }
    }

    /**
     * Track screen on/off so the ongoing notification never shows a clip
     * preview while the device is locked — the manifest-registered
     * [ScreenOffReceiver] clears the system clipboard, but the notification
     * text must be masked too (it's visible on the lock screen).
     */
    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOff = true
                        updateNotification(if (maskContent) null else lastPreview)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenOff = false
                        updateNotification(lastPreview)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching { registerReceiver(receiver, filter) }
        screenReceiver = receiver
    }

    private fun createChannel() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        } catch (_: Exception) { /* ignore */ }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { screenReceiver?.let { unregisterReceiver(it) } }
        screenReceiver = null
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "clipboard_monitor"
        const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 800L

        const val ACTION_PAUSE = "com.clipvault.manager.PAUSE"
        const val ACTION_RESUME = "com.clipvault.manager.RESUME"
        private const val REVERIFY_INTERVAL_MS = 30_000L

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, ClipboardMonitorService::class.java)
                ctx.startForegroundService(i)
            } catch (_: Exception) { /* defensive — never crash caller */ }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, ClipboardMonitorService::class.java)) }
            catch (_: Exception) { }
        }
    }
}