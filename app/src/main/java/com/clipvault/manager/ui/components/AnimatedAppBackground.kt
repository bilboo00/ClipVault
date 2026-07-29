package com.clipvault.manager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

private val BrandStart = Color(0xFF7C6FF7)
private val BrandMid = Color(0xFF6366F1)
private val BrandEnd = Color(0xFFEC4899)
private val BrandDeep = Color(0xFF312E81)
private val AmoledGlow = Color(0xFF4338CA)

/**
 * Full-screen animated brand gradient that sits behind every screen.
 *
 * Two soft "blobs" of brand colour drift slowly across the surface; they're
 * rendered with Multiply (light theme) or Screen (dark / AMOLED) blend so
 * the underlying Material surface still drives the page chrome.
 *
 * Performance:
 *  • Only ticks while the host lifecycle is at least STARTED.
 *  • Low-frequency animation (~8 s), one pass on the canvas.
 *  • Two animated values total → no recomposition of siblings.
 */
@Composable
fun AnimatedAppBackground(
    isDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var running by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            running = when (e) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> false
                else -> running
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val transition = rememberInfiniteTransition(label = "app-bg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )
    val t = if (running) phase else 0f

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBrandBackground(t = t, isDark = isDark, isAmoled = isAmoled)
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
        coreColor = BrandStart,
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
        coreColor = BrandEnd,
        edgeColor = BrandMid,
        alpha = blobAlpha,
        blendMode = blobBlend
    )

    if (isAmoled) {
        // Subtle indigo highlight for AMOLED — keeps the deep blacks from feeling flat
        val c3 = Offset(w * 0.5f + travel * 0.5f * t, h * 0.45f)
        drawCircleGlow(
            center = c3,
            radius = w * 0.65f,
            coreColor = AmoledGlow,
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