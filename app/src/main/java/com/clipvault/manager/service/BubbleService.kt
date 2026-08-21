package com.clipvault.manager.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.clipvault.manager.app.MainActivity
import com.clipvault.manager.R
import com.clipvault.manager.service.startForegroundCompat
import com.clipvault.manager.data.repository.ClipboardRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class BubbleService : Service() {

    @Inject lateinit var repository: ClipboardRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var moved = false
    private var lastUpdateX = 0
    private var lastUpdateY = 0
    private var density: Float = 1f

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(NOTIF_ID, buildNotification(), currentFgsType())
        addBubbleSafely()
    }

    /**
     * On API 34+ use SPECIAL_USE (matches manifest). Below that, use
     * DATA_SYNC as a safe fallback so the service still boots on older
     * devices — the manifest declares both via property fallback.
     */
    private fun currentFgsType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun addBubbleSafely() {
        if (bubbleView != null) return
        try {
            // null parent is intentional: the bubble is added directly to
            // WindowManager, not attached to any view hierarchy.
            val view = LayoutInflater.from(this).inflate(R.layout.layout_bubble, null)
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            density = resources.displayMetrics.density

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 400
            }
            lastUpdateX = lp.x
            lastUpdateY = lp.y

            view.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.x
                        initialY = lp.y
                        touchStartX = event.rawX
                        touchStartY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchStartX
                        val dy = event.rawY - touchStartY
                        if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) moved = true
                        val newX = (initialX + dx).toInt()
                        val newY = (initialY + dy).toInt()
                        // Throttle updateViewLayout: only push to WindowManager
                        // when the position has actually moved by ≥ 2 dp from
                        // the last update, otherwise every touch sample would
                        // trigger a layout pass.
                        val thresholdPx = UPDATE_THRESHOLD_DP * density
                        if (abs(newX - lastUpdateX) >= thresholdPx ||
                            abs(newY - lastUpdateY) >= thresholdPx) {
                            lp.x = newX
                            lp.y = newY
                            lastUpdateX = newX
                            lastUpdateY = newY
                            runCatching { windowManager?.updateViewLayout(view, lp) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) onBubbleTap()
                        view.performClick()
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(view, lp)
            bubbleView = view
            params = lp
        } catch (e: Exception) {
            // No overlay permission, OEM restriction, or system policy block —
            // back out cleanly so we don't leave the service half-alive
            android.util.Log.w("BubbleService", "Failed to add bubble overlay", e)
            stopSelf()
        }
    }

    private fun onBubbleTap() {
        scope.launch {
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData? = cm.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    toast("Clipboard is empty")
                    return@launch
                }
                val text = clip.getItemAt(0).coerceToText(this@BubbleService)
                    ?.toString().orEmpty()
                if (text.isBlank()) {
                    toast("Clipboard is empty")
                    return@launch
                }
                val saved = repository.saveIfNew(text, sourceLabel = "bubble")
                toast(if (saved != null) "Saved to clipboard history" else "Already saved")
            } catch (_: SecurityException) {
                toast("Cannot read clipboard right now")
            } catch (_: Exception) {
                toast("Could not save")
            }
        }
    }

    private fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(this@BubbleService, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(getString(R.string.bubble_notif_title))
            .setContentText(getString(R.string.bubble_notif_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(R.drawable.ic_bubble, getString(R.string.action_stop), stop)
            .build()
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
                    description = getString(R.string.bubble_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        } catch (_: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            bubbleView?.let { windowManager?.removeView(it) }
        }
        bubbleView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "bubble_overlay"
        const val NOTIF_ID = 1002
        const val ACTION_STOP = "com.clipvault.manager.BUBBLE_STOP"
        private const val TOUCH_SLOP = 10
        private const val UPDATE_THRESHOLD_DP = 2

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, BubbleService::class.java)
                ctx.startForegroundService(i)
            } catch (_: Exception) {
                // Bubble can fail to start (overlay perm revoked, etc.) —
                // the toggle UI will be the source of truth
            }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, BubbleService::class.java).setAction(ACTION_STOP)) }
            catch (_: Exception) { }
        }
    }
}