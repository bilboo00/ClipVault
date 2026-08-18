package com.clipvault.manager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import com.clipvault.manager.ui.theme.BrandDeep
import com.clipvault.manager.ui.theme.BrandIndigo
import com.clipvault.manager.ui.theme.BrandViolet

private val OrbStartColor = BrandViolet
private val OrbMidColor = BrandIndigo
private val OrbEndColor = BrandDeep

/**
 * Radial-gradient orb with a slow, shallow pulse.
 *
 * Performance notes:
 *  • Animation only ticks while the host lifecycle is at least STARTED —
 *    pauses automatically when the screen is off / activity backgrounded.
 *  • Period is 4 s with a 4 % scale range — visually soft, frame-cheap.
 *  • Only the inner Canvas reads the animated value, so surrounding
 *    composables don't recompose every frame.
 */
@Composable
fun AnimatedGradientOrb(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var running by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
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

    val transition = rememberInfiniteTransition(label = "orb")
    val animatedScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb-scale"
    )
    val scale = if (running) animatedScale else 1f

    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            drawOrb(scale)
        }
        Icon(
            imageVector = Icons.Outlined.ContentPaste,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
    }
}

private fun DrawScope.drawOrb(scaleFactor: Float) {
    val brush = Brush.radialGradient(
        colors = listOf(OrbStartColor, OrbMidColor, OrbEndColor),
        center = Offset(size.width / 2, size.height / 2),
        radius = size.minDimension / 1.4f
    )
    scale(scaleFactor, pivot = center) {
        drawCircle(brush = brush, radius = size.minDimension / 2)
    }
}

@Composable
fun EmptyStateWithOrb(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            AnimatedGradientOrb()
            Spacer(modifier = Modifier.size(20.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}