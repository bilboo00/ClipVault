package com.clipvault.manager.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent roles for one palette in one brightness mode. Only the accent
 * families (primary / secondary / tertiary) are overridden per palette — the
 * neutral surface hierarchy (canvas / cards / outline tones) stays shared so
 * every palette keeps the design export's tonal stepping intact.
 */
data class PaletteAccents(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

/**
 * User-selectable colour palettes surfaced in Settings → Appearance.
 *
 * IDs are persisted in DataStore — append new entries with fresh IDs, never
 * renumber or reorder existing ones. [fromId] falls back to the default for
 * unknown values, so a downgrade from a future build stays safe.
 */
enum class AppPalette(
    val id: Int,
    val label: String,
    val light: PaletteAccents,
    val dark: PaletteAccents
) {
    /** The design export's original periwinkle scheme. */
    INDIGO_BLOOM(
        id = 0,
        label = "Indigo Bloom",
        light = PaletteAccents(
            primary = Color(0xFF434574),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF5B5D8E),
            onPrimaryContainer = Color(0xFFDBDAFF),
            secondary = Color(0xFF5F5C70),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE2DDF4),
            onSecondaryContainer = Color(0xFF636074),
            tertiary = Color(0xFF713B21),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF8D5236),
            onTertiaryContainer = Color(0xFFFFD4C2)
        ),
        dark = PaletteAccents(
            primary = Color(0xFFC1C2FA),
            onPrimary = Color(0xFF2A2C5B),
            primaryContainer = Color(0xFF434575),
            onPrimaryContainer = Color(0xFFE1E0FF),
            secondary = Color(0xFFC8C4DB),
            onSecondary = Color(0xFF313046),
            secondaryContainer = Color(0xFF474558),
            onSecondaryContainer = Color(0xFFE5DFF7),
            tertiary = Color(0xFFFFB695),
            onTertiary = Color(0xFF552009),
            tertiaryContainer = Color(0xFF6D381E),
            onTertiaryContainer = Color(0xFFFFD4C2)
        )
    ),

    /** Cool blue-grey — quieter than Indigo Bloom, same tonal logic. */
    SLATE(
        id = 1,
        label = "Slate",
        light = PaletteAccents(
            primary = Color(0xFF47536B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF5F6C86),
            onPrimaryContainer = Color(0xFFD9E2F8),
            secondary = Color(0xFF575C6F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDEE1F3),
            onSecondaryContainer = Color(0xFF4C4F5E),
            // Warm clay accent kept across every palette: `tertiary` paints
            // liked hearts and the "Other" segment, so it must stay warm.
            tertiary = Color(0xFF713B21),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF8D5236),
            onTertiaryContainer = Color(0xFFFFD4C2)
        ),
        dark = PaletteAccents(
            primary = Color(0xFFB9C4DC),
            onPrimary = Color(0xFF2B3247),
            primaryContainer = Color(0xFF404D66),
            onPrimaryContainer = Color(0xFFD9E2F8),
            secondary = Color(0xFFC4C6D6),
            onSecondary = Color(0xFF2D3040),
            secondaryContainer = Color(0xFF434656),
            onSecondaryContainer = Color(0xFFDEE1F3),
            tertiary = Color(0xFFFFB695),
            onTertiary = Color(0xFF552009),
            tertiaryContainer = Color(0xFF6D381E),
            onTertiaryContainer = Color(0xFFFFD4C2)
        )
    ),

    /** Terracotta warm with a sage accent — the export's clay family promoted. */
    CLAY(
        id = 2,
        label = "Clay",
        light = PaletteAccents(
            primary = Color(0xFF8A4A2E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFA96145),
            onPrimaryContainer = Color(0xFFFFDBCC),
            secondary = Color(0xFF74574A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF3DCD2),
            onSecondaryContainer = Color(0xFF5A4033),
            // Dusty rose — keeps the warm accent readable next to terracotta.
            tertiary = Color(0xFF7D4A56),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF996270),
            onTertiaryContainer = Color(0xFFFFD9E2)
        ),
        dark = PaletteAccents(
            primary = Color(0xFFFFB59A),
            onPrimary = Color(0xFF522005),
            primaryContainer = Color(0xFF70391F),
            onPrimaryContainer = Color(0xFFFFDBCC),
            secondary = Color(0xFFE5BFB1),
            onSecondary = Color(0xFF402A21),
            secondaryContainer = Color(0xFF594036),
            onSecondaryContainer = Color(0xFFF3DCD2),
            tertiary = Color(0xFFF2B8C6),
            onTertiary = Color(0xFF4A2530),
            tertiaryContainer = Color(0xFF633A46),
            onTertiaryContainer = Color(0xFFFFD9E2)
        )
    ),

    /** Soft violet — a lighter, friendlier cousin of Indigo Bloom. */
    LAVENDER(
        id = 3,
        label = "Lavender",
        light = PaletteAccents(
            primary = Color(0xFF6757A5),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF8172C2),
            onPrimaryContainer = Color(0xFFE9E5FF),
            secondary = Color(0xFF635B71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE9DEF8),
            onSecondaryContainer = Color(0xFF584F63),
            tertiary = Color(0xFF7A4E62),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFF956077),
            onTertiaryContainer = Color(0xFFFFD8E6)
        ),
        dark = PaletteAccents(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF38265F),
            primaryContainer = Color(0xFF4F3D78),
            onPrimaryContainer = Color(0xFFE9E5FF),
            secondary = Color(0xFFCDC2DA),
            onSecondary = Color(0xFF352F40),
            secondaryContainer = Color(0xFF4C4557),
            onSecondaryContainer = Color(0xFFE9DEF8),
            tertiary = Color(0xFFE8B4CA),
            onTertiary = Color(0xFF462434),
            tertiaryContainer = Color(0xFF61394C),
            onTertiaryContainer = Color(0xFFFFD8E6)
        )
    );

    companion object {
        fun fromId(id: Int): AppPalette = entries.firstOrNull { it.id == id } ?: INDIGO_BLOOM
    }
}
