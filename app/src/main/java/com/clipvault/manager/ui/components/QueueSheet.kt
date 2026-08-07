package com.clipvault.manager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clipvault.manager.data.local.entity.ClipEntity

/**
 * Paste-queue tray: shows queued clips, the "next to paste" item, and lets the
 * user reorder, remove, copy-next, or clear the queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    items: List<ClipEntity>,
    currentIndex: Int,
    onCopy: (ClipEntity) -> Unit,
    onCopyNext: () -> Unit,
    onMove: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Paste queue", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (items.isEmpty()) "Queue is empty"
                        else "Next to paste: #${currentIndex + 1} of ${items.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (items.isEmpty()) {
                Text(
                    "Add clips from the detail screen with the Queue button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.height(320.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val isNext = index == currentIndex
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isNext)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable { onCopy(item) }) {
                                    Text(
                                        if (isNext) "NEXT" else "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isNext)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        item.content.take(80),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isNext)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onCopy(item) },
                                    enabled = !isNext
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy this clip",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMove(item.id, index - 1) },
                                    enabled = index > 0
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowUpward,
                                        contentDescription = "Move up",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMove(item.id, index + 1) },
                                    enabled = index < items.size - 1
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        contentDescription = "Move down",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { onRemove(item.id) }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove from queue",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCopyNext,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy next")
                }
                OutlinedButton(
                    onClick = onClear,
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }
        }
    }
}
