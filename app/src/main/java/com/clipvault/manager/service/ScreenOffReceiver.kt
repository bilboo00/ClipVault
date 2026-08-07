package com.clipvault.manager.service

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/**
 * Clears the system clipboard when the screen turns off.
 * Registered in the manifest for [Intent.ACTION_SCREEN_OFF].
 */
class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(
                android.content.ClipData.newPlainText("", "")
            )
        } catch (_: Exception) {
            // Defensive — screen-off receiver must never crash
        }
    }
}
