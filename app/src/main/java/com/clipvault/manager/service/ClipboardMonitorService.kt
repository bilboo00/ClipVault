package com.clipvault.manager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardMonitorService : Service() {

    @Inject lateinit var repository: ClipboardRepository
    @Inject lateinit var settingsManager: SettingsManager

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            // Without this handler any uncaught exception in a scope coroutine
            // (e.g. a DataStore IOException surfacing from the settings
            // combine below) would crash the whole process via the default
            // handler. Capture is best-effort by contract — log and survive.
            android.util.Log.w("ClipboardMonitor", "scope failure", e)
        }
    )
    private val clipboardMutex = Mutex()
    private var pollJob: kotlinx.coroutines.Job? = null
    private var lastSeen: String? = null
    private var lastImageUri: String? = null
    private var lastImagePath: String? = null
    private var lastVerifyAt: Long = 0L
    private lateinit var clipboardManager: ClipboardManager
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null
    @Volatile private var maskContent = false
    @Volatile private var screenOff = false
    @Volatile private var lastPreview: String? = null
    /** Content confirmed present in history this session — lets unchanged
     *  polls return without touching the DB at all. */
    @Volatile private var lastSavedConfirmed: String? = null

    /** Extra delay added to the poll interval while the platform keeps
     *  denying background clipboard reads (app not focused). */
    @Volatile private var denialBackoffMs: Long = 0L
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
        startForegroundCompat(NOTIFICATION_ID, buildNotification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_DETACH)
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
        // so the first listener event doesn't re-save the stale clipboard.
        // Important: do this even if SecurityException is thrown, so the
        // listener has a real baseline to compare against.
        primeLastSeenWithRetries()
        listener = ClipboardManager.OnPrimaryClipChangedListener {
            scope.launch { clipboardMutex.withLock { pollClipboardSafely() } }
        }
            .also { clipboardManager.addPrimaryClipChangedListener(it) }
        if (pollJob?.isActive != true) {
            pollJob = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(POLL_INTERVAL_MS + denialBackoffMs)
                    val gotSomething = clipboardMutex.withLock { pollClipboardSafely() }
                    if (gotSomething) {
                        denialBackoffMs = 0L
                    } else {
                        // Android 10+ denies background reads while the app
                        // isn't focused ("Denying clipboard access … not in
                        // focus" from ClipboardService). Back the futile poll
                        // off progressively — 5s→15s→30s→60s cap — to stop
                        // battery drain and log spam; capture now happens on
                        // app-open instead. Reset instantly on any success.
                        denialBackoffMs =
                            minOf(denialBackoffMs + 10_000L, MAX_DENIAL_BACKOFF_MS)
                                .coerceAtLeast(10_000L)
                    }
                }
            }
        }
    }

    /**
     * Try to read the clipboard and set [lastSeen] to its current content.
     * Retries with increasing backoff because on Android 10+ the very first
     * primaryClip access from a freshly-started foreground service can throw
     * SecurityException — we must succeed before the listener kicks in,
     * otherwise the first event would treat the stale clipboard as a new copy.
     *
     * Each retry attempt is treated independently: an empty/transient-null
     * result on the first try does NOT abort the loop, otherwise a clipboard
     * that's momentarily inaccessible at service-start would leave lastSeen
     * un-primed forever and the very next user copy would be treated as new
     * (correct) — but a stale-clipboard "set lastSeen = stale" race could
     * suppress a real new copy if the listener fires before any retry has
     * succeeded.
     */
    private fun primeLastSeenWithRetries() {
        scope.launch {
            clipboardMutex.withLock {
                val backoffs = longArrayOf(0L, 100L, 300L, 700L)
                for (delayMs in backoffs) {
                    if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                    val captured = try {
                        val clip = clipboardManager.primaryClip
                        if (clip == null || clip.itemCount == 0) null
                        else {
                            val item = clip.getItemAt(0)
                            item.text?.toString()?.takeIf { it.isNotEmpty() }
                                ?: item.coerceToText(this@ClipboardMonitorService)?.toString()
                        }
                    } catch (_: SecurityException) {
                        null
                    } catch (_: Exception) {
                        null
                    }
                    if (!captured.isNullOrEmpty()) {
                        lastSeen = captured
                        return@withLock
                    }
                }
                // Couldn't read clipboard in time — leave lastSeen as-is (null) so
                // the first listener event captures the actual current content.
            }
        }
    }

    private fun stopListening() {
        listener?.let { clipboardManager.removePrimaryClipChangedListener(it) }
        listener = null
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Try to capture the current clipboard.
     * @return true when a non-empty clipboard was actually READ (saved,
     * deduped, or skipped-by-suppression — any successful read), false when
     * access was denied/empty. Drives the adaptive backoff: on modern Android
     * a background read is rejected while the app isn't focused, so repeated
     * false results mean "platform says no", not "user copied nothing".
     */
    private suspend fun pollClipboardSafely(): Boolean {
        try {
            val clip: ClipData? = clipboardManager.primaryClip
            if (clip == null || clip.itemCount == 0) return false
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
                // DB checks are throttled so the listener doesn't hammer Room.
                if (uriKey == lastImageUri) {
                    val path = lastImagePath
                    if (path != null) {
                        if (repository.isImageSuppressed(path)) return true
                        if (now - lastVerifyAt < REVERIFY_INTERVAL_MS) return true
                        lastVerifyAt = now
                        if (repository.imageExists(path)) return true
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
                return true
            }

            val text = withContext(Dispatchers.Default) {
                item.text?.toString()?.takeIf { it.isNotEmpty() }
                    ?: item.coerceToText(this@ClipboardMonitorService)?.toString()
            }.orEmpty()
            if (text.isBlank()) return false

            if (text != lastSeen) {
                // New clipboard value — delete-suppressions for the previous
                // content no longer apply.
                repository.clearDeleteSuppressions()
                lastSavedConfirmed = null
            } else if (text == lastSavedConfirmed) {
                // Unchanged content we've already confirmed is in history —
                // return without ANY DB work. This keeps the periodic
                // safety-net poll free in the steady state; previously every
                // poll re-ran a COUNT(*) full scan on the un-indexed content
                // column, which showed up as app-wide jank on large histories.
                return true
            }

            if (text == lastSeen) {
                // Same content as the last capture — never re-add content the
                // user explicitly deleted while it's still on the clipboard;
                // otherwise still verify it wasn't deleted from history, but
                // throttle the DB query so repeated events don't fire
                // many COUNTs.
                if (repository.isContentSuppressed(text)) return true
                val now = System.currentTimeMillis()
                if (now - lastVerifyAt < REVERIFY_INTERVAL_MS) return true
                lastVerifyAt = now
                if (repository.contentExists(text)) {
                    lastSavedConfirmed = text
                    return true
                }
            }
            val saved = repository.saveIfNew(text, sourceLabel = null)
            // Advance lastSeen even when the save was deduped, so we don't
            // re-query the DB on every event for an already-known clip.
            lastSeen = text
            if (saved != null) {
                lastSavedConfirmed = text
                updateNotification(text)
            }
            return true
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        return false
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        runCatching { screenReceiver?.let { unregisterReceiver(it) } }
        screenReceiver = null
        stopListening()
        scheduleRestart()
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("SpecifyJobSchedulerIdRange")
    private fun scheduleRestart() {
        try {
            val js = getSystemService(JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(this, RestartJobService::class.java)
            )
                .setMinimumLatency(RESTART_LATENCY_MS)
                .setPersisted(true)
                .build()
            js.schedule(job)
        } catch (_: Exception) { /* best-effort restart */ }
    }

    companion object {
        const val CHANNEL_ID = "clipboard_monitor"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.clipvault.manager.PAUSE"
        const val ACTION_RESUME = "com.clipvault.manager.RESUME"
        // Listener events give instant capture; these are safety-net values
        // only. The 2 s/5 s pair tried earlier caused constant un-indexed
        // COUNT scans and app-wide jank — don't tighten without a fast path.
        private const val REVERIFY_INTERVAL_MS = 30_000L
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_DENIAL_BACKOFF_MS = 55_000L
        private const val JOB_ID = 4101
        private const val RESTART_LATENCY_MS = 15L * 60L * 1000L

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

    class RestartJobService : JobService() {
        override fun onStartJob(params: JobParameters?): Boolean {
            ClipboardMonitorService.start(applicationContext)
            return false
        }

        override fun onStopJob(params: JobParameters?): Boolean = false
    }
}