package com.clipvault.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.clipvault.manager.ui.components.AnimatedAppBackground

// Light scheme — token set taken verbatim from the design export. Note the
// tonal hierarchy: canvas is `surface`, list cards are `surfaceContainer`,
// nested preview pods are `surfaceContainerLowest`.
private val LightScheme = lightColorScheme(
    primary = PeriwinklePrimary,
    onPrimary = PeriwinkleOnPrimary,
    primaryContainer = PeriwinklePrimaryContainer,
    onPrimaryContainer = PeriwinkleOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = ClayTertiary,
    onTertiary = ClayOnTertiary,
    tertiaryContainer = ClayTertiaryContainer,
    onTertiaryContainer = ClayOnTertiaryContainer,
    background = SurfaceCanvas,
    onBackground = OnSurfaceTone,
    surface = SurfaceCanvas,
    onSurface = OnSurfaceTone,
    surfaceVariant = SurfaceVariantTone,
    onSurfaceVariant = OnSurfaceVariantTone,
    surfaceTint = SurfaceTintTone,
    surfaceContainerLowest = SurfaceContainerLowestTone,
    surfaceContainerLow = SurfaceContainerLowTone,
    surfaceContainer = SurfaceContainerTone,
    surfaceContainerHigh = SurfaceContainerHighTone,
    surfaceContainerHighest = SurfaceContainerHighestTone,
    outline = OutlineTone,
    outlineVariant = OutlineVariantTone,
    inverseSurface = InverseSurfaceTone,
    inverseOnSurface = InverseOnSurfaceTone,
    inversePrimary = InversePrimaryTone,
    error = ErrorColor,
    onError = OnErrorColor,
    errorContainer = ErrorContainerColor,
    onErrorContainer = OnErrorContainerColor
)

// Dark scheme sets the colour families only — the neutrals fall back to the
// M3 dark baseline (`surface #141218`), exactly as the export's dark notes do.
private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

// AMOLED variant: the page canvas is pure black, but the container ladder
// stays intact so cards, the search pill and sheets remain visible. The
// previous mapping sent every `surfaceContainer*` slot to black and left
// cards indistinguishable from the page — the search pill, clip cards and
// preview pods all vanished. `surface` is the canvas here (Scaffold picks
// `background`), so cards keep the raised tonal steps instead.
private val AmoledScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = AmoledBackground,
    onBackground = Color(0xFFE6E0E9),
    surface = AmoledBackground,
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = AmoledCardSurface,
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = AmoledCardSurface,
    surfaceContainerLowest = Color(0xFF0A090D),
    surfaceContainerLow = Color(0xFF111016),
    surfaceContainer = AmoledCardSurface,
    surfaceContainerHigh = Color(0xFF1E1C24),
    surfaceContainerHighest = Color(0xFF26242E),
    outline = Color(0xFF49454F),
    outlineVariant = Color(0xFF2C2A33),
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

/**
 * Theme override codes used in DataStore:
 *  0 → Follow system
 *  1 → Light
 *  2 → Dark
 *  3 → AMOLED (pure black)
 *
 * [palette] swaps the accent families (primary / secondary / tertiary) while
 * keeping the shared neutral surface hierarchy, so every palette preserves
 * the export's tonal card/canvas stepping. When [dynamicColor] is enabled on
 * Android 12+, wallpaper-derived colours win and the palette is bypassed
 * (AMOLED always keeps pure black surfaces and still applies the palette's
 * dark accents).
 */
@Composable
fun ClipboardManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: dynamic palettes wash out the designed tonal steps
    // (cards become invisible against the page in both light and dark),
    // leaving only AMOLED with visible separation. Opt in explicitly.
    dynamicColor: Boolean = false,
    themeOverride: Int = 0,
    palette: AppPalette = AppPalette.INDIGO_BLOOM,
    content: @Composable () -> Unit
) {
    val isAmoled = themeOverride == 3
    val isDark = when (themeOverride) {
        1 -> false
        2, 3 -> true
        else -> darkTheme
    }
    val context = LocalContext.current
    val scheme = remember(themeOverride, darkTheme, dynamicColor, palette) {
        when (themeOverride) {
            3 -> AmoledScheme.withAccents(palette.dark, palette.light.primary)
            else -> when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                isDark -> DarkScheme.withAccents(palette.dark, palette.light.primary)
                else -> LightScheme.withAccents(palette.light, palette.dark.primary)
            }
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

/**
 * Replaces the accent families on [this] scheme and points `inversePrimary`
 * at the opposite mode's primary (it is only ever shown on `inverseSurface`).
 */
private fun ColorScheme.withAccents(
    accents: PaletteAccents,
    inversePrimary: Color
): ColorScheme = copy(
    primary = accents.primary,
    onPrimary = accents.onPrimary,
    primaryContainer = accents.primaryContainer,
    onPrimaryContainer = accents.onPrimaryContainer,
    secondary = accents.secondary,
    onSecondary = accents.onSecondary,
    secondaryContainer = accents.secondaryContainer,
    onSecondaryContainer = accents.onSecondaryContainer,
    tertiary = accents.tertiary,
    onTertiary = accents.onTertiary,
    tertiaryContainer = accents.tertiaryContainer,
    onTertiaryContainer = accents.onTertiaryContainer,
    inversePrimary = inversePrimary
)
