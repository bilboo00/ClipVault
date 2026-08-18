package com.clipvault.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Replacement for Material 3's SnackbarHost that stacks up to [maxStack]
 * snackbars simultaneously (newest at the bottom).
 *
 * Each item has a unique key so adding/removing animates smoothly with
 * a vertical slide+fade. The host itself handles sequential showing —
 * unlike the default SnackbarHostState which only shows one at a time.
 */
@Composable
fun StackedSnackbarHost(
    hostState: StackedSnackbarHostState,
    modifier: Modifier = Modifier,
    maxStack: Int = 3
) {
    // Column with bottom alignment stacks snackbars upward (newest at the
    // bottom, directly above the screen edge) instead of overlapping.
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        val items = hostState.items
        items.takeLast(maxStack).forEach { item ->
            androidx.compose.runtime.key(item.id) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(150)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(120))
                ) {
                    Snackbar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        action = item.actionLabel?.let { label ->
                            {
                                TextButton(onClick = { item.onAction?.invoke() }) { Text(label) }
                            }
                        }
                    ) {
                        Text(item.message)
                    }
                }
            }
        }
    }
}

/**
 * Data holder for stacked snackbars. Push messages via [show] and they
 * stay visible for [durationMs] before being auto-dismissed.
 *
 * [show] is a suspending function that returns the action label if the user
 * clicked the action button, or null if the snackbar timed out / was dismissed
 * without action. This mirrors Material 3's SnackbarHostState contract.
 */
class StackedSnackbarHostState {
    private val _items = mutableStateOf<List<SnackItem>>(emptyList())
    val items: List<SnackItem> get() = _items.value

    suspend fun show(
        message: String,
        actionLabel: String? = null,
        durationMs: Long = 3_500L
    ): String? = coroutineScope {
        val id = UUID.randomUUID().toString()
        val resultDeferred = CompletableDeferred<String?>()

        val item = SnackItem(
            id = id,
            message = message,
            actionLabel = actionLabel,
            onAction = {
                if (resultDeferred.complete(actionLabel)) {
                    dismiss(id)
                }
            }
        )
        _items.value = _items.value + item

        try {
            // Auto-dismiss after duration. If the action was clicked first, this
            // is a no-op (deferred already completed) and dismiss(id) is idempotent.
            launch {
                delay(durationMs)
                if (resultDeferred.complete(null)) {
                    dismiss(id)
                }
            }

            resultDeferred.await()
        } finally {
            // Runs on normal completion (no-op, item already dismissed) AND on
            // caller cancellation — without this the item would be leaked in
            // _items forever when the showing coroutine is cancelled.
            dismiss(id)
        }
    }

    fun dismiss(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
    }
}

data class SnackItem(
    val id: String,
    val message: String,
    val actionLabel: String?,
    val onAction: (() -> Unit)? = null
)

@Composable
fun rememberStackedSnackbarHostState(): StackedSnackbarHostState =
    remember { StackedSnackbarHostState() }