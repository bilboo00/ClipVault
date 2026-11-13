package com.clipvault.manager.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Palette from the "Dynamic Material Clipboard" design export (Stitch).
// Light values are the export's token set verbatim; dark/AMOLED are derived
// from its dark notes (`surface #141218`, `primaryContainer #434575` dark).
// ---------------------------------------------------------------------------

val PeriwinklePrimary = Color(0xFF434574)
val PeriwinkleOnPrimary = Color(0xFFFFFFFF)
val PeriwinklePrimaryContainer = Color(0xFF5B5D8E)
val PeriwinkleOnPrimaryContainer = Color(0xFFDBDAFF)

val SlateSecondary = Color(0xFF5F5C70)
val SlateOnSecondary = Color(0xFFFFFFFF)
val SlateSecondaryContainer = Color(0xFFE2DDF4)
val SlateOnSecondaryContainer = Color(0xFF636074)

val ClayTertiary = Color(0xFF713B21)
val ClayOnTertiary = Color(0xFFFFFFFF)
val ClayTertiaryContainer = Color(0xFF8D5236)
val ClayOnTertiaryContainer = Color(0xFFFFD4C2)

// Soft fixed tints — pinned cards ("tertiary-fixed/30" peach, "secondary-fixed/40" lavender).
val ClayFixed = Color(0xFFFFDBCC)
val ClayFixedDim = Color(0xFFFFB695)
val LavenderFixed = Color(0xFFE1E0FF)
val LavenderSecondaryFixed = Color(0xFFE5DFF7)

// Tonal surface hierarchy (light) — canvas white-lavender, cards a step darker.
val SurfaceCanvas = Color(0xFFFCF8FC)
val OnSurfaceTone = Color(0xFF1B1B1E)
val SurfaceContainerLowestTone = Color(0xFFFFFFFF)
val SurfaceContainerLowTone = Color(0xFFF6F3F6)
val SurfaceContainerTone = Color(0xFFF0EDF0)
val SurfaceContainerHighTone = Color(0xFFEAE7EB)
val SurfaceContainerHighestTone = Color(0xFFE4E1E5)
val SurfaceVariantTone = Color(0xFFE4E1E5)
val OnSurfaceVariantTone = Color(0xFF46464F)
val OutlineTone = Color(0xFF777680)
val OutlineVariantTone = Color(0xFFC7C5D0)
val SurfaceTintTone = Color(0xFF585A8B)

// Inverse (copy toast / snackbars).
val InverseSurfaceTone = Color(0xFF303033)
val InverseOnSurfaceTone = Color(0xFFF3F0F3)
val InversePrimaryTone = Color(0xFFC1C2FA)

// Dark scheme — colour families only; neutrals stay on the M3 dark baseline
// (`surface #141218`), which is exactly what the export's dark notes use.
val DarkPrimary = Color(0xFFC1C2FA)
val DarkOnPrimary = Color(0xFF2A2C5B)
val DarkPrimaryContainer = Color(0xFF434575)
val DarkOnPrimaryContainer = Color(0xFFE1E0FF)

val DarkSecondary = Color(0xFFC8C4DB)
val DarkOnSecondary = Color(0xFF313046)
val DarkSecondaryContainer = Color(0xFF474558)
val DarkOnSecondaryContainer = Color(0xFFE5DFF7)

val DarkTertiary = Color(0xFFFFB695)
val DarkOnTertiary = Color(0xFF552009)
val DarkTertiaryContainer = Color(0xFF6D381E)
val DarkOnTertiaryContainer = Color(0xFFFFD4C2)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// Error (light).
val ErrorColor = Color(0xFFBA1A1A)
val OnErrorColor = Color(0xFFFFFFFF)
val ErrorContainerColor = Color(0xFFFFDAD6)
val OnErrorContainerColor = Color(0xFF93000A)

// AMOLED (pure black for OLED battery savings)
val AmoledBackground = Color(0xFF000000)
// Cards use `surface`, so AMOLED needs exactly one raised step above black.
val AmoledCardSurface = Color(0xFF17161C)

// Shared brand palette — used by AnimatedAppBackground, the empty-state orb,
// and anywhere the brand gradient appears (single source of truth). Recoloured
// to the periwinkle/clay families of the new design system.
val BrandViolet = Color(0xFF8B86D9)
val BrandIndigo = Color(0xFF5B5D8E)
val BrandPink = Color(0xFFE8B8A6)
val BrandDeep = Color(0xFF34366B)
val BrandAmoledGlow = Color(0xFF3E3F6E)
