package com.clipvault.manager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.clipvault.manager.ui.theme.Motion

/**
 * Crossfade + pop animation when an icon swaps between two states (e.g.
 * pinned ↔ unpinned, favorited ↔ not). Keeps the action feeling alive
 * without the jank of the M3 default SegmentedButton check-tick.
 */
@Composable
fun AnimatedToggleIcon(
    active: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    AnimatedContent(
        targetState = active,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f, animationSpec = tween(Motion.Short)) +
                fadeIn(animationSpec = tween(Motion.Short))) togetherWith
                (scaleOut(targetScale = 0.6f, animationSpec = tween(Motion.Short)) +
                    fadeOut(animationSpec = tween(Motion.Short)))
        },
        label = "toggle-icon"
    ) { isActive ->
        Icon(
            imageVector = if (isActive) activeIcon else inactiveIcon,
            contentDescription = contentDescription,
            tint = tint ?: androidx.compose.material3.LocalContentColor.current,
            modifier = modifier
        )
    }
}