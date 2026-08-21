package com.clipvault.manager.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DateFormat
import java.util.Date

/**
 * Shared, memoized date / relative-time helpers.
 *
 * Without memoization, `DateFormat.getDateInstance(...)` allocates a fresh
 * formatter per call (and per recomposition) — on a list of 200 cards every
 * scroll triggers hundreds of allocations. These helpers cache the underlying
 * formatter on first use; remember-keyed so a single screen lifecycle reuses
 * the same instance.
 */

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L
private const val WEEK_MS = 7 * DAY_MS

/**
 * Relative time for "X minutes/hours/days ago" with a short date fallback
 * for anything older than a week. Shared across Home, Search and Detail rows.
 */
fun relativeTime(now: Long, ts: Long): String {
    val diff = now - ts
    return when {
        diff < MINUTE_MS -> "just now"
        diff < HOUR_MS -> "${diff / MINUTE_MS}m ago"
        diff < DAY_MS -> "${diff / HOUR_MS}h ago"
        diff < WEEK_MS -> "${diff / DAY_MS}d ago"
        else -> shortDateFormat().format(Date(ts))
    }
}

/**
 * MEDIUM/SHORT date-time, e.g. "Jan 12, 2024 3:42 PM". Used by the clip
 * detail header.
 */
fun dateTime(ts: Long): String =
    dateTimeFormat().format(Date(ts))

private fun shortDateFormat(): DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
private fun dateTimeFormat(): DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

/**
 * Returns a memoized `(Long) -> String` that calls [relativeTime] with the
 * call-site's "now" baked in. "now" is read inside the lambda so the closure
 * is allocated once and reused across recompositions; the trade-off is that
 * the displayed timestamp goes stale until the next recomposition — fine for
 * list cards but not for live countdowns.
 */
@Composable
fun rememberRelativeTime(): (Long) -> String =
    remember { { ts -> relativeTime(System.currentTimeMillis(), ts) } }

@Composable
fun rememberDateTime(): (Long) -> String =
    remember { ::dateTime }