package com.clipvault.manager.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reorder helper for `LazyColumn` items. Long-press an item to start a drag,
 * then drag it up/down. While dragging, the item swaps positions in the
 * [items] list as it crosses neighbours. On release, [onDragEnd] is called
 * so the caller can persist the final order.
 *
 * Item matching is done via the LazyColumn item key (equal to the clip id),
 * so headers or other non-reorderable rows are safely skipped.
 */
fun <T : Any> Modifier.draggableItem(
    listState: LazyListState,
    itemId: Any,
    items: SnapshotStateList<T>,
    equalityOf: (T) -> Any? = { (it as? WithId)?.id },
    onMove: (itemId: Any, fromIndex: Int, toIndex: Int) -> Unit = { _, _, _ -> },
    onDragEnd: () -> Unit = {}
): Modifier = this.pointerInput(itemId) {
    var startIndex = -1
    var lastPointerY = 0f
    var initialPointerY = 0f
    var totalDeltaY = 0f

    detectDragGesturesAfterLongPress(
        onDragStart = { offset: Offset ->
            startIndex = items.indexOfFirst { equalityOf(it) == itemId }
            initialPointerY = offset.y
            lastPointerY = offset.y
            totalDeltaY = 0f
        },
        onDrag = { change, _ ->
            val currentY = change.position.y
            val stepDelta = currentY - lastPointerY
            lastPointerY = currentY
            totalDeltaY += stepDelta
            change.consume()

            val pointerY = initialPointerY + totalDeltaY
            val target = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { item ->
                    pointerY in item.offset.toFloat()..(item.offset + item.size).toFloat()
                } ?: return@detectDragGesturesAfterLongPress

            // Map the LazyColumn item (keyed by clip id) back to our list.
            val toIndex = items.indexOfFirst { equalityOf(it) == target.key }
            if (startIndex >= 0 && toIndex >= 0 && toIndex != startIndex) {
                val from = startIndex
                val moved = items.removeAt(from)
                items.add(toIndex, moved)
                startIndex = toIndex
                onMove(itemId, from, toIndex)
            }
        },
        onDragEnd = {
            startIndex = -1
            onDragEnd()
        },
        onDragCancel = {
            startIndex = -1
            onDragEnd()
        }
    )
}

interface WithId {
    val id: Any
}
