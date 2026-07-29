package com.clipvault.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * State for the hero animation. Set [target] when a card is copied and the
 * host overlay reads it to animate a flying ghost toward the FAB.
 */
class CopyHeroState {
    var source: Offset? = null
    var target: Offset? = null
    var visible: Boolean = false

    /** Capture source, target, then briefly show. */
    fun launch(from: Offset, to: Offset) {
        source = from
        target = to
        visible = true
    }

    fun clear() {
        source = null
        target = null
        visible = false
    }
}

@Composable
fun rememberCopyHeroState(): CopyHeroState = remember { CopyHeroState() }

/**
 * Top-level overlay that renders a "flying card" between source and target
 * for ~500 ms whenever [state.visible] is true.
 *
 * Must be placed in the same Box as both the source card and the FAB, so
 * that the absolute offsets are in the same coordinate space.
 */
@Composable
fun CopyHeroOverlay(
    state: CopyHeroState,
    fabPosition: () -> Offset?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 56.dp.toPx() }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(animationSpec = tween(80)) + scaleIn(initialScale = 0.6f, animationSpec = tween(80)),
        exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.4f, animationSpec = tween(120)),
        modifier = modifier.fillMaxSize()
    ) {
        val source = state.source
        val target = state.target ?: fabPosition()
        if (source != null && target != null) {
            // Animate from source to target over 450ms
            var current by remember(source.x, source.y, target.x, target.y) {
                mutableStateOf(source)
            }
            LaunchedEffect(source, target) {
                val steps = 14
                for (i in 1..steps) {
                    val t = i / steps.toFloat()
                    val eased = LinearOutSlowInEasing.transform(t)
                    current = Offset(
                        source.x + (target.x - source.x) * eased,
                        source.y + (target.y - source.y) * eased
                    )
                    delay(28)
                }
            }
            val scale by animateFloatAsState(
                targetValue = 0.5f,
                animationSpec = tween(420),
                label = "hero-scale"
            )

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (current.x - sizePx / 2f).roundToInt(),
                                (current.y - sizePx / 2f).roundToInt()
                            )
                        }
                        .size(56.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Hide after the fly completes
            LaunchedEffect(source, target) {
                delay(450)
                state.clear()
            }
        }
    }
}