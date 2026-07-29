package com.clipvault.manager.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a Haptics controller scoped to the current composition.
 * Usage:
 *   val haptics = rememberHaptics()
 *   IconButton(onClick = { haptics.light(); doStuff() }) { ... }
 */
@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context) }
}