package com.clipvault.manager.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * Calls startForeground with the correct API based on Android version.
 * On API 29+ the 3-arg form MUST be used when the service declares
 * a foregroundServiceType in its manifest — otherwise the system throws
 * MissingForegroundServiceTypeException and crashes the app process.
 *
 * @param type pass the matching FOREGROUND_SERVICE_TYPE_* constant for
 *   your service, or null for "no declared type" (legacy 2-arg).
 */
fun Service.startForegroundCompat(
    id: Int,
    notification: Notification,
    type: Int? = null
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type != null) {
        try {
            startForeground(id, notification, type)
            return
        } catch (_: Exception) {
            // Fall through to legacy form
        }
    }
    startForeground(id, notification)
}