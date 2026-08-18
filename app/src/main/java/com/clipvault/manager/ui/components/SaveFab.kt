package com.clipvault.manager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.clipvault.manager.ui.theme.Motion

/**
 * A pulsing "save now" FAB. Scales up briefly on tap with a spring animation,
 * giving the button a tactile, responsive feel.
 */
@Composable
fun SaveFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Save now",
    isPulsing: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isPulsing) 1.15f else 1f,
        animationSpec = Motion.BouncySpring,
        label = "fab-pulse"
    )

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        text = { Text(label, style = MaterialTheme.typography.titleSmall) }
    )
}