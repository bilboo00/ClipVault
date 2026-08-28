package com.clipvault.manager.util

import android.content.Context
import android.os.Build
import com.clipvault.manager.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Minimal on-device crash reporter.
 *
 * Installs a chained [Thread.UncaughtExceptionHandler] that writes every
 * uncaught exception to `filesDir/crash_reports/crash_<epoch>.txt` before
 * delegating to the previous handler (so the normal system crash flow is
 * preserved). This exists because there's no adb attached in the user's
 * setup — the written report can be copied out from the Settings screen,
 * giving an exact stack trace instead of a verbal description of "the app
 * crashed".
 *
 * Reports are capped at [MAX_REPORTS]; oldest are deleted.
 */
object CrashReporter {

    private const val DIR_NAME = "crash_reports"
    private const val MAX_REPORTS = 10

    fun install(appContext: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext.applicationContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let the reporter itself break crash handling.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun reportsDir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Newest-first list of stored crash reports (may be empty). */
    fun pendingReports(context: Context): List<File> =
        reportsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun clearAll(context: Context) {
        pendingReports(context).forEach { runCatching { it.delete() } }
    }

    fun latestReportText(context: Context): String? =
        pendingReports(context).firstOrNull()?.readText()

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = reportsDir(context)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "crash_${System.currentTimeMillis()}.txt")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        var cause: Throwable? = throwable.cause
        var depth = 1
        while (cause != null && depth <= 5) {
            sw.append("\n\nCaused by (level $depth):\n")
            cause.printStackTrace(PrintWriter(sw))
            cause = cause.cause
            depth++
        }

        file.writeText(
            buildString {
                appendLine("ClipVault crash report")
                appendLine("time:        ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
                appendLine("appVersion:  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("device:      ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("thread:      ${thread.name}")
                appendLine()
                appendLine("stacktrace:")
                append(sw.toString())
            }
        )

        // Trim to the newest MAX_REPORTS files.
        val all = pendingReports(context)
        if (all.size > MAX_REPORTS) {
            all.drop(MAX_REPORTS).forEach { runCatching { it.delete() } }
        }
    }
}
