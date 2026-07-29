package com.clipvault.manager.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Reorder helper for `LazyColumn` items. Long-press an item to start a drag,
 * then drag it up/down. While dragging, the item swaps positions in the
 * [items] list as it crosses neighbours. On release, [onMove] is called
 * once with the final (from, to) so the caller can persist.
 */
fun <T : Any> Modifier.draggableItem(
    listState: LazyListState,
    itemId: Any,
    items: SnapshotStateList<T>,
    equalityOf: (T) -> Any? = { (it as? WithId)?.id },
    onMove: (itemId: Any, fromIndex: Int, toIndex: Int) -> Unit
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
            val current = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { item: LazyListItemInfo ->
                    val center = item.offset + item.size / 2f
                    pointerY in item.offset.toFloat()..(item.offset + item.size).toFloat() ||
                        kotlin.math.abs(pointerY - center) < item.size / 2f
                } ?: return@detectDragGesturesAfterLongPress

            if (startIndex >= 0 && current.index != startIndex) {
                val from = startIndex
                val to = current.index
                if (from in items.indices && to in items.indices) {
                    val moved = items.removeAt(from)
                    items.add(to, moved)
                    startIndex = to
                    onMove(itemId, from, to)
                }
            }
        },
        onDragEnd = { startIndex = -1 },
        onDragCancel = { startIndex = -1 }
    )
}

interface WithId {
    val id: Any
}