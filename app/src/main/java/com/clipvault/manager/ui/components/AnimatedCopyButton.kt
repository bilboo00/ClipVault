package com.clipvault.manager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A copy button that morphs from clipboard → check icon with a spring bounce
 * when activated. Used on clip rows for inline copy feedback.
 */
@Composable
fun AnimatedCopyButton(
    isCopied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.primary,
        animationSpec = tween(200),
        label = "copy-tint"
    )
    val bg by animateColorAsState(
        targetValue = if (isCopied) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        animationSpec = tween(200),
        label = "copy-bg"
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isCopied,
            transitionSpec = {
                (scaleIn(initialScale = 0.5f) + fadeIn(animationSpec = tween(150))) togetherWith
                    scaleOut(targetScale = 0.5f) + fadeOut(animationSpec = tween(150))
            },
            label = "copy-icon"
        ) { copied ->
            if (copied) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Copied",
                    tint = tint,
                    modifier = Modifier
                        .size(16.dp)
                        .scale(1.15f)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}