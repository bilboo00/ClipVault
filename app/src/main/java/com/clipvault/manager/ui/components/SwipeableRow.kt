@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.clipvault.manager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clipvault.manager.haptic.rememberHaptics
import com.clipvault.manager.ui.theme.Motion

enum class SwipeAction { Pin, Delete }

/**
 * Wraps content with two swipe actions:
 *   • Swipe right  → Pin toggle (handled by caller via onSwipe)
 *   • Swipe left   → Delete (handled by caller via onSwipe)
 *
 * Fires a tick haptic the moment the user's finger crosses the action threshold —
 * the moment they "commit" to the gesture but before release.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SwipeableRow(
    onSwipe: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptics = rememberHaptics()
    var lastDirection by remember { mutableStateOf(SwipeToDismissBoxValue.Settled) }
    var armed by remember { mutableStateOf(false) }

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!armed) {
                        armed = true
                        haptics.tick()
                    }
                    onSwipe(SwipeAction.Pin)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (!armed) {
                        armed = true
                        haptics.heavy()
                    }
                    onSwipe(SwipeAction.Delete)
                    false
                }
                SwipeToDismissBoxValue.Settled -> {
                    armed = false
                    true
                }
            }
        },
        positionalThreshold = { distance -> distance * 0.4f }
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            // Trigger a tick when crossing threshold (direction is non-Settled)
            LaunchedEffect(direction) {
                if (direction != SwipeToDismissBoxValue.Settled && lastDirection == SwipeToDismissBoxValue.Settled) {
                    haptics.tick()
                }
                lastDirection = direction
            }
            SwipeBackground(direction)
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        content()
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val isPinSide = direction == SwipeToDismissBoxValue.StartToEnd
    val isDelSide = direction == SwipeToDismissBoxValue.EndToStart

    val color by animateColorAsState(
        targetValue = when {
            isPinSide -> MaterialTheme.colorScheme.tertiaryContainer
            isDelSide -> MaterialTheme.colorScheme.errorContainer
            else -> Color.Transparent
        },
        animationSpec = tween(Motion.Short),
        label = "swipe-bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = if (isPinSide) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDelSide) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                androidx.compose.material3.Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge
                )
            } else if (isPinSide) {
                androidx.compose.material3.Text(
                    "Pin",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge
                )
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}