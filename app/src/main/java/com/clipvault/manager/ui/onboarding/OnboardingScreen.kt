package com.clipvault.manager.ui.onboarding

import com.clipvault.manager.ui.theme.Motion
import com.clipvault.manager.ui.theme.Spacing
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Xxl)
        ) {
            ProgressHeader(
                current = state.currentPage + 1,
                total = state.totalPages,
                onBack = {
                    haptics.light()
                    viewModel.previous()
                },
                onSkip = {
                    haptics.medium()
                    viewModel.finish()
                    onFinished()
                },
                showBack = state.currentPage > 0,
                showSkip = state.currentPage < state.totalPages - 1
            )

            Spacer(modifier = Modifier.height(Spacing.Xxxl))

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
    onSkip: () -> Unit,
    showBack: Boolean,
    showSkip: Boolean
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
        if (showSkip && current < total) {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        } else {
            Text(
                text = "$current of $total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(Spacing.Md))
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
        body = "ClipVault saves everything you copy so you can find and re-paste it later — even days after you copied it.",
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
        BigIcon(Icons.Outlined.AccessibilityNew)
        Spacer(modifier = Modifier.height(Spacing.Lg))
        StatusRow(
            label = if (granted) "Accessibility enabled" else "Accessibility not enabled",
            isGranted = granted
        )
        Spacer(modifier = Modifier.height(Spacing.Xxl))
        Text(
            text = "Capture copies in the background",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Text(
            text = "Android 10+ blocks background clipboard access for privacy. " +
                "Enabling this optional Accessibility service lets the app detect when you copy " +
                "in any other app — even when it's not open.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Xxl))
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
        Spacer(modifier = Modifier.height(Spacing.Sm))
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
        Spacer(modifier = Modifier.height(Spacing.Xxl))
        Text(
            text = "Quick access shortcuts",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        Text(
            text = "Two optional ways to grab a copy from anywhere:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Xxl))

        ShortcutRow(
            icon = Icons.Outlined.BubbleChart,
            title = "Floating bubble",
            description = "A small draggable bubble that saves the current clipboard when you tap it.",
            status = if (overlayGranted) "Overlay permission granted" else "Overlay permission needed",
            isGranted = overlayGranted,
            actionLabel = if (overlayGranted) "Granted" else "Grant permission",
            onAction = if (!overlayGranted) onGrantOverlay else null
        )

        Spacer(modifier = Modifier.height(Spacing.Lg))

        ShortcutRow(
            icon = Icons.Outlined.Tune,
            title = "Quick Settings tile",
            description = "Pull down the notification shade, tap the pencil icon, then drag " +
                "\"Clipboard history\" onto the bar.",
            status = "Manual setup",
            isGranted = false,
            actionLabel = null,
            onAction = null
        )

        Spacer(modifier = Modifier.height(Spacing.Sm))
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
        Spacer(modifier = Modifier.height(Spacing.Lg))
        if (statusBadge != null) {
            StatusRow(
                label = if (statusBadge == StatusBadge.Active) "Running" else "Paused",
                isGranted = statusBadge == StatusBadge.Active
            )
            Spacer(modifier = Modifier.height(Spacing.Lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (footer != null) {
            Spacer(modifier = Modifier.height(Spacing.Xxl))
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
    Box(contentAlignment = Alignment.Center) {
        // Soft brand bloom behind the tile — same hero language as the Lock
        // screen and the Home / Insights hero cards.
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
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
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, isGranted: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isGranted)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Outlined.Check else Icons.Outlined.Info,
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
                fontWeight = FontWeight.Medium,
                color = if (isGranted)
                    MaterialTheme.colorScheme.primary
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
        Column(modifier = Modifier.padding(Spacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(Spacing.Md))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(Spacing.Sm))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.Md))
            StatusRow(label = status, isGranted = isGranted)
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(Spacing.Md))
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
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(modifier = Modifier.padding(0.dp)) { content() }
    }
}