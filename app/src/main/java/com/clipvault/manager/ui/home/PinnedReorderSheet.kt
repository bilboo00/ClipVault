package com.clipvault.manager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.domain.model.Clip
import com.clipvault.manager.ui.theme.Spacing

/**
 * Bottom sheet that lets the user change the order of pinned clips with
 * explicit up/down controls. The old long-press drag affordance never worked
 * reliably (it operated on feed rows, while pinned clips render in a separate
 * two-column section), so reordering is an intentional, discoverable action
 * instead of a hidden gesture.
 *
 * [clips] must already be in display order (`sortOrder`). The sheet keeps a
 * local working copy and only persists on "Save order", so a half-finished
 * reorder is discarded on dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedReorderSheet(
    clips: List<Clip>,
    onDismiss: () -> Unit,
    onConfirm: (orderedIds: List<Long>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = remember(clips) {
        mutableStateListOf<Clip>().apply { addAll(clips) }
    }
    val changed = draft.map { it.id } != clips.map { it.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Reorder pinned",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    start = Spacing.Xxl,
                    top = Spacing.Sm,
                    bottom = Spacing.Xs
                )
            )
            Text(
                text = "Use the arrows to change the order pinned clips appear in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.Xxl,
                    end = Spacing.Xxl,
                    bottom = Spacing.Md
                )
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = Spacing.Lg, vertical = Spacing.Xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
            ) {
                itemsIndexed(draft, key = { _, clip -> clip.id }) { index, clip ->
                    ReorderRow(
                        index = index,
                        total = draft.size,
                        clip = clip,
                        onMoveUp = { draft.add(index - 1, draft.removeAt(index)) },
                        onMoveDown = { draft.add(index + 1, draft.removeAt(index)) }
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Xxl, vertical = Spacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onConfirm(draft.map { it.id }) },
                    enabled = changed
                ) { Text("Save order") }
            }
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

@Composable
private fun ReorderRow(
    index: Int,
    total: Int,
    clip: Clip,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.Md,
                end = Spacing.Xs,
                top = Spacing.Xs,
                bottom = Spacing.Xs
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (clip.type == ClipType.IMAGE) "Image" else clip.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = clip.type.displayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Move item ${index + 1} up",
                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Move item ${index + 1} down",
                    tint = if (index < total - 1) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

private fun ClipType.displayLabel(): String = when (this) {
    ClipType.TEXT -> "Text"
    ClipType.URL -> "Link"
    ClipType.EMAIL -> "Email"
    ClipType.PHONE -> "Phone"
    ClipType.CODE -> "Code"
    ClipType.NUMBER -> "Number"
    ClipType.COLOR_HEX -> "Color"
    ClipType.IBAN -> "IBAN"
    ClipType.UUID -> "UUID"
    ClipType.JSON -> "JSON"
    ClipType.WALLET_ADDRESS -> "Wallet"
    ClipType.IP -> "IP"
    ClipType.IMAGE -> "Image"
    ClipType.OTP -> "One-time code"
}
