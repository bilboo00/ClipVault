package com.clipvault.manager.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.haptic.rememberHaptics

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            ProgressHeader(
                current = state.currentPage + 1,
                total = state.totalPages,
                onBack = {
                    haptics.light()
                    viewModel.previous()
                },
                showBack = state.currentPage > 0
            )

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = state.currentPage,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(400),
                        initialOffsetX = { it * direction / 4 }
                    ) + fadeIn(animationSpec = tween(400)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(400))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { -it * direction / 4 }
                        ) + fadeOut(animationSpec = tween(300)) +
                            scaleOut(targetScale = 0.96f, animationSpec = tween(300)))
                },
                label = "onboarding-page"
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> BackgroundServicePage(monitoringOn = state.monitoringOn)
                    2 -> AccessibilityPage(
                        granted = state.accessibilityGranted,
                        onEnable = {
                            haptics.medium()
                            viewModel.enableAccessibility()
                        }
                    )
                    3 -> BubbleAndTilePage(
                        overlayGranted = state.overlayGranted,
                        onGrantOverlay = {
                            haptics.medium()
                            viewModel.enableOverlay()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            BottomActions(
                isLastPage = state.currentPage == state.totalPages - 1,
                onNext = {
                    if (state.currentPage == state.totalPages - 1) {
                        haptics.success()
                        viewModel.finish()
                        onFinished()
                    } else {
                        haptics.light()
                        viewModel.next()
                    }
                }
            )
        }
    }
}

@Composable
private fun ProgressHeader(
    current: Int,
    total: Int,
    onBack: () -> Unit,
    showBack: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        } else {
            Spacer(modifier = Modifier.width(64.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$current of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { current.toFloat() / total.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
private fun WelcomePage() {
    PageScaffold(
        icon = Icons.Outlined.ContentPaste,
        title = "Never lose a copy again",
        body = "Clipboard Manager saves everything you copy so you can find and re-paste it later — even days after you copied it.",
        footer = "All data stays on your phone. No accounts, no cloud, no tracking."
    )
}

@Composable
private fun BackgroundServicePage(monitoringOn: Boolean) {
    PageScaffold(
        icon = Icons.Outlined.ContentPaste,
        title = "Already saving copies",
        body = "A small notification runs while you use your phone. It captures everything you copy into your history list.",
        footer = "You can pause it any time from the notification or Settings.",
        statusBadge = if (monitoringOn) StatusBadge.Active else StatusBadge.Inactive
    )
}

@Composable
private fun AccessibilityPage(
    granted: Boolean,
    onEnable: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BigIcon(Icons.Filled.Accessibility)
        Spacer(modifier = Modifier.height(16.dp))
        StatusRow(
            label = if (granted) "Accessibility enabled" else "Accessibility not enabled",
            isGranted = granted
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Capture copies in the background",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Android 10+ blocks background clipboard access for privacy. " +
                "Enabling this optional Accessibility service lets the app detect when you copy " +
                "in any other app — even when it's not open.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (granted) {
            OutlinedButton(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
                Text("Open settings")
            }
        } else {
            Button(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Enable accessibility")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Optional — you can skip and enable later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BubbleAndTilePage(
    overlayGranted: Boolean,
    onGrantOverlay: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BigIcon(Icons.Outlined.BubbleChart)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Quick access shortcuts",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Two optional ways to grab a copy from anywhere:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        ShortcutRow(
            icon = Icons.Outlined.BubbleChart,
            title = "Floating bubble",
            description = "A small draggable bubble that saves the current clipboard when you tap it.",
            status = if (overlayGranted) "Overlay permission granted" else "Overlay permission needed",
            isGranted = overlayGranted,
            actionLabel = if (overlayGranted) "Granted" else "Grant permission",
            onAction = if (!overlayGranted) onGrantOverlay else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        ShortcutRow(
            icon = Icons.Filled.Settings,
            title = "Quick Settings tile",
            description = "Pull down the notification shade, tap the pencil icon, then drag " +
                "\"Clipboard history\" onto the bar.",
            status = "Manual setup",
            isGranted = false,
            actionLabel = null,
            onAction = null
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Both are optional. You can enable them later from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PageScaffold(
    icon: ImageVector,
    title: String,
    body: String,
    footer: String? = null,
    statusBadge: StatusBadge? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BigIcon(icon)
        Spacer(modifier = Modifier.height(16.dp))
        if (statusBadge != null) {
            StatusRow(
                label = if (statusBadge == StatusBadge.Active) "Running" else "Paused",
                isGranted = statusBadge == StatusBadge.Active
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (footer != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private enum class StatusBadge { Active, Inactive }

@Composable
private fun BigIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun StatusRow(label: String, isGranted: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isGranted)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isGranted)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShortcutRow(
    icon: ImageVector,
    title: String,
    description: String,
    status: String,
    isGranted: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow(label = status, isGranted = isGranted)
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BottomActions(
    isLastPage: Boolean,
    onNext: () -> Unit
) {
    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(if (isLastPage) "Get started" else "Continue")
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(modifier = Modifier.padding(0.dp)) { content() }
    }
}