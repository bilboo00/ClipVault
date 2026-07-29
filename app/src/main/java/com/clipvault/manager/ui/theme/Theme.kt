package com.clipvault.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.clipvault.manager.ui.components.AnimatedAppBackground

private val LightScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = IndigoOnPrimary,
    primaryContainer = IndigoPrimaryContainer,
    onPrimaryContainer = IndigoOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    error = ErrorColor,
    onError = OnError
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorColor,
    onError = OnError
)

private val AmoledScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = AmoledBackground,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = AmoledOutline,
    error = ErrorColor,
    onError = OnError
)

/**
 * Theme override codes used in DataStore:
 *  0 → Follow system
 *  1 → Light
 *  2 → Dark
 *  3 → AMOLED (pure black)
 */
@Composable
fun ClipboardManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeOverride: Int = 0,
    content: @Composable () -> Unit
) {
    val isAmoled = themeOverride == 3
    val isDark = when (themeOverride) {
        1 -> false
        2, 3 -> true
        else -> darkTheme
    }
    val scheme = when (themeOverride) {
        3 -> AmoledScheme // AMOLED ignores dynamic color — pure black is the point
        else -> when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val ctx = LocalContext.current
                if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            }
            isDark -> DarkScheme
            else -> LightScheme
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography
    ) {
        AnimatedAppBackground(isDark = isDark, isAmoled = isAmoled) {
            content()
        }
    }
}