package com.asura.finanzas.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a `LazyColumn`, matching what the web does with dnd-kit:
 * the drag starts only from an explicit handle, so a plain tap on the row still
 * opens it. Rows shuffle live while dragging; the new order is sent once, on
 * drop — the same single `reorder_*` call the web makes.
 *
 * Item keys must be stable, and `onMove` has to reorder the caller's own list
 * so the visual result survives recomposition.
 */
class ReorderState(
    val listState: LazyListState,
    /**
     * Which lazy-list indices are draggable. A LazyColumn also holds headers and
     * notices, and a row must never swap places with one of those, so the range
     * is given explicitly and `onMove` receives indices relative to it.
     */
    private val range: () -> IntRange,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
    private val scope: CoroutineScope,
) {
    /** Index into the lazy list of the row being dragged, or null when idle. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** How far the dragged row has travelled from its resting slot. */
    var offsetY by mutableFloatStateOf(0f)
        private set

    private fun itemAt(index: Int) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    fun onDragStart(index: Int) {
        draggingIndex = index
        offsetY = 0f
    }

    fun onDrag(delta: Float) {
        val current = draggingIndex ?: return
        offsetY += delta

        val dragged = itemAt(current) ?: return
        // Where the dragged row's edges are right now, mid-gesture.
        val top = dragged.offset + offsetY
        val bottom = top + dragged.size

        // Swap as soon as the dragged row's leading edge passes a neighbour's
        // midpoint; one step at a time keeps it stable during a fast flick.
        val draggable = range()
        val target = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { candidate ->
                candidate.index != current && candidate.index in draggable &&
                    if (candidate.index > current) {
                        bottom > candidate.offset + candidate.size / 2
                    } else {
                        top < candidate.offset + candidate.size / 2
                    }
            }
            ?: return

        onMove(current - draggable.first, target.index - draggable.first)
        // The row keeps following the finger: it has taken the target's slot, so
        // the accumulated offset shrinks by exactly the distance jumped.
        offsetY += dragged.offset - target.offset
        draggingIndex = target.index
    }

    fun onDragEnd() {
        val moved = draggingIndex != null
        draggingIndex = null
        offsetY = 0f
        if (moved) scope.launch { onDrop() }
    }

    fun onDragCancel() {
        draggingIndex = null
        offsetY = 0f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    scope: CoroutineScope,
    range: () -> IntRange,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): ReorderState = remember(listState) { ReorderState(listState, range, onMove, onDrop, scope) }

/**
 * The grip that starts a drag. Put it on a small, obvious area of the row —
 * anywhere else stays tappable, which is the whole point of the handle.
 */
@Composable
fun ReorderHandle(
    state: ReorderState,
    /**
     * Stable identity of the row — its database id, never its position. Keying
     * the gesture on the index would restart the detector the moment a swap
     * moves the row, aborting the drag halfway through.
     */
    key: Any,
    /** Read lazily, so the handler always sees the row's current position. */
    index: () -> Int,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.wallets_reorder)
    Icon(
        imageVector = Icons.Filled.DragIndicator,
        contentDescription = label,
        tint = Broke.colors.fgSubtle,
        modifier = modifier
            .size(24.dp)
            .semantics { contentDescription = label }
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { state.onDragStart(index()) },
                    onDrag = { change, drag ->
                        change.consume()
                        state.onDrag(drag.y)
                    },
                    onDragEnd = { state.onDragEnd() },
                    onDragCancel = { state.onDragCancel() },
                )
            },
    )
}
