package com.clipvault.manager.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single spacing scale for the whole app (4dp grid).
 *
 * Prefer these over magic dp values so density stays consistent across
 * screens. Shapes intentionally live in [androidx.compose.material3.Shapes]
 * via MaterialTheme — don't invent parallel radii here.
 */
object Spacing {
    val None: Dp = 0.dp
    val Xxs: Dp = 2.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 20.dp
    val Xxl: Dp = 24.dp
    val Xxxl: Dp = 32.dp

    /** Standard horizontal screen padding. */
    val ScreenHorizontal: Dp = 16.dp

    /** Standard gap between list cards. */
    val CardGap: Dp = 10.dp

    /** Inner padding of a standard card. */
    val CardPadding: Dp = 14.dp
}
