package com.clipvault.manager.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Expand template placeholders inside a snippet's content.
 *
 * Supported tokens:
 *   {{date}}       → today's date (locale default, e.g. Jul 26, 2026)
 *   {{date:fmt}}  → today's date with custom format (e.g. {{date:yyyy-MM-dd}})
 *   {{date+N}}    → date N days from today (e.g. {{date+3}} → 3 days ahead)
 *   {{date-N}}    → date N days before today
 *   {{time}}      → current time (locale default)
 *   {{time:fmt}}  → custom time format (e.g. {{time:HH:mm}})
 *   {{year}}      → 4-digit year
 *   {{month}}     → month name
 *   {{day}}       → day of month
 *   {{clipboard}} → current device clipboard content
 *
 * Unknown tokens are left as-is so users notice and can fix typos.
 */
object SnippetExpander {
    private val tokenRegex = Regex(
        "\\{\\{(date|time|year|month|day|clipboard)(?:([+-]\\d+)|(:[^}]+))?\\}\\}"
    )

    fun expand(
        template: String,
        clipboardContent: String? = null,
        locale: Locale = Locale.getDefault(),
        now: () -> Date = { Date() }
    ): String {
        return tokenRegex.replace(template) { match ->
            val key = match.groupValues[1]
            val offset = match.groupValues[2].toIntOrNull()
            val format = match.groupValues[3].removePrefix(":").takeIf { it.isNotEmpty() }

            when (key) {
                "clipboard" -> clipboardContent.orEmpty()
                "date" -> formatDate(now(), offset, format, locale)
                "time" -> formatTime(now(), format, locale)
                "year" -> SimpleDateFormat("yyyy", locale).format(now())
                "month" -> SimpleDateFormat("MMMM", locale).format(now())
                "day" -> SimpleDateFormat("d", locale).format(now())
                else -> match.value
            }
        }
    }

    private fun formatDate(
        now: Date,
        offsetDays: Int?,
        format: String?,
        locale: Locale
    ): String {
        val cal = Calendar.getInstance(locale).apply {
            time = now
            offsetDays?.let { add(Calendar.DAY_OF_YEAR, it) }
        }
        val pattern = format ?: "MMM d, yyyy"
        return SimpleDateFormat(pattern, locale).format(cal.time)
    }

    private fun formatTime(now: Date, format: String?, locale: Locale): String {
        val pattern = format ?: "h:mm a"
        return SimpleDateFormat(pattern, locale).format(now)
    }
}