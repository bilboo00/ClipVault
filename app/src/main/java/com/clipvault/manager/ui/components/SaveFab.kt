package com.clipvault.manager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.clipvault.manager.ui.theme.Motion

/**
 * Compact squircle "save now" FAB (56.dp, 28.dp corners) in
 * `primaryContainer`, matching the design export's floating action button.
 * Scales up briefly on tap with a spring animation, giving the button a
 * tactile, responsive feel.
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

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = label,
            modifier = Modifier.size(28.dp)
        )
    }
}
