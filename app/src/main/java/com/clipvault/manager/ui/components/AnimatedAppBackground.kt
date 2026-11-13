package com.clipvault.manager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

import com.clipvault.manager.ui.theme.BrandAmoledGlow
import com.clipvault.manager.ui.theme.BrandDeep
import com.clipvault.manager.ui.theme.BrandIndigo
import com.clipvault.manager.ui.theme.BrandPink
import com.clipvault.manager.ui.theme.BrandViolet

/**
 * Full-screen brand gradient that sits behind every screen.
 *
 * Two soft "blobs" of brand colour drift slowly across the surface; they're
 * rendered with Multiply (light theme) or Screen (dark) blend so the
 * underlying Material surface still drives the page chrome.
 *
 * AMOLED skips this entirely — pure black wins for OLED battery, and the
 * blend-mode screen pass was the dominant cost in dark mode. Skipping it
 * drops a fullscreen radial-gradient Canvas from every transition.
 *
 * Performance:
 *  • The gradient is STATIC — no infinite transition. Every screen Scaffold
 *    paints an opaque background today, so the blobs are fully covered and an
 *    always-on animation would burn GPU every frame for zero visible effect
 *    (this showed up as app-wide sluggishness on low-end devices).
 *  • If surfaces ever go translucent again, re-introduce the phase animation
 *    (tween 8000ms, LinearEasing, Reverse) driven only while STARTED.
 *  • Single Canvas, one draw pass, no recomposition cost after first draw.
 */
@Composable
fun AnimatedAppBackground(
    isDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!isAmoled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Fixed midpoint of the old drift range.
                drawBrandBackground(t = 0.5f, isDark = isDark, isAmoled = isAmoled)
            }
        }
        content()
    }
}

private fun DrawScope.drawBrandBackground(t: Float, isDark: Boolean, isAmoled: Boolean) {
    val base = if (isAmoled) Color.Black else Color.Transparent

    val blobAlpha = when {
        isAmoled -> 0.55f
        isDark -> 0.30f
        else -> 0.55f
    }
    val blobBlend = when {
        isAmoled -> BlendMode.Screen
        isDark -> BlendMode.Screen
        else -> BlendMode.Multiply
    }

    val w = size.width
    val h = size.height
    val travel = w * 0.35f

    // Blob 1 — top-left brand violet → drifts to top-right
    val c1 = Offset(-w * 0.15f + travel * t, -h * 0.20f + travel * 0.5f * t)
    val r1 = w * 0.75f
    drawCircleGlow(
        center = c1,
        radius = r1,
        coreColor = BrandViolet,
        edgeColor = BrandDeep,
        alpha = blobAlpha,
        blendMode = blobBlend
    )

    // Blob 2 — bottom-right pink → drifts to bottom-left
    val c2 = Offset(w * 1.05f - travel * t, h * 1.15f - travel * 0.5f * t)
    val r2 = w * 0.70f
    drawCircleGlow(
        center = c2,
        radius = r2,
        coreColor = BrandPink,
        edgeColor = BrandIndigo,
        alpha = blobAlpha,
        blendMode = blobBlend
    )

    if (isAmoled) {
        // Subtle indigo highlight for AMOLED — keeps the deep blacks from feeling flat
        val c3 = Offset(w * 0.5f + travel * 0.5f * t, h * 0.45f)
        drawCircleGlow(
            center = c3,
            radius = w * 0.65f,
            coreColor = BrandAmoledGlow,
            edgeColor = Color.Transparent,
            alpha = 0.25f,
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawCircleGlow(
    center: Offset,
    radius: Float,
    coreColor: Color,
    edgeColor: Color,
    alpha: Float,
    blendMode: BlendMode
) {
    val colors = if (edgeColor.alpha == 0f) {
        listOf(coreColor.copy(alpha = alpha), Color.Transparent)
    } else {
        listOf(coreColor.copy(alpha = alpha), edgeColor.copy(alpha = 0f))
    }
    val brush = Brush.radialGradient(
        colors = colors,
        center = center,
        radius = radius
    )
    drawCircle(
        brush = brush,
        radius = radius,
        center = center,
        blendMode = blendMode
    )
}