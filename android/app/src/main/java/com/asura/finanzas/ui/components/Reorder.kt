package com.asura.finanzas.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

/** How close to an edge the finger has to get before the list starts scrolling. */
private const val EDGE_PX = 120f

/** Pixels per drag event to scroll when held against an edge. */
private const val EDGE_SPEED = 12f

/**
 * Drag-to-reorder for a `LazyColumn`, matching what the web does with dnd-kit:
 * the drag starts only from an explicit handle, so a plain tap on the row still
 * opens it. Rows shuffle live while dragging; the new order is sent once, on
 * drop — the same single `reorder_*` call the web makes.
 *
 * The row being dragged is tracked by its **key**, never by its position. Its
 * index changes the instant a swap happens, and the list has not recomposed
 * yet at that point, so anything derived from the old index reads a stale
 * layout — which is what made dragging jump around and land on the wrong row.
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
    /** Key of the row being dragged, or null when idle. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** How far the dragged row has travelled from its resting slot. */
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** Live layout of the dragged row, looked up by key so a swap can't lose it. */
    private val dragged: LazyListItemInfo?
        get() = draggingKey?.let { key ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    fun onDragStart(key: Any) {
        draggingKey = key
        offsetY = 0f
    }

    fun onDrag(delta: Float) {
        val info = dragged ?: return
        offsetY += delta

        // Where the row's middle sits right now, mid-gesture.
        val centre = info.offset + info.size / 2f + offsetY

        // Rows have very different heights here (a wallet with apartados is far
        // taller than one without), so the target is whichever row actually
        // contains that point rather than one guessed from a fixed step.
        val draggable = range()
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != info.index && it.index in draggable &&
                centre >= it.offset && centre <= it.offset + it.size
        }

        if (target != null) {
            onMove(info.index - draggable.first, target.index - draggable.first)
            // The row keeps following the finger: it has taken the target's
            // slot, so the accumulated offset shrinks by the distance jumped.
            offsetY += info.offset - target.offset
        }

        autoScroll(centre)
    }

    /**
     * Held against the top or bottom edge, the list scrolls so a row can travel
     * further than one screenful. Without this a wallet could never be moved
     * past the handful of cards that happen to be visible.
     */
    private fun autoScroll(centre: Float) {
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val amount = when {
            centre < EDGE_PX -> -EDGE_SPEED
            centre > viewportEnd - EDGE_PX -> EDGE_SPEED
            else -> return
        }
        scope.launch { listState.scrollBy(amount) }
    }

    fun onDragEnd() {
        val moved = draggingKey != null
        draggingKey = null
        offsetY = 0f
        if (moved) scope.launch { onDrop() }
    }

    fun onDragCancel() {
        draggingKey = null
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
): ReorderState {
    // The state outlives the lambdas: callers rebuild them whenever their list
    // is rebuilt (a query lands, a saved order arrives), so it has to call the
    // LATEST ones. Holding the first set means dragging reorders a list that is
    // no longer the one on screen — the rows never move.
    val currentRange = rememberUpdatedState(range)
    val currentMove = rememberUpdatedState(onMove)
    val currentDrop = rememberUpdatedState(onDrop)
    return remember(listState) {
        ReorderState(
            listState = listState,
            range = { currentRange.value() },
            onMove = { from, to -> currentMove.value(from, to) },
            onDrop = { currentDrop.value() },
            scope = scope,
        )
    }
}

/**
 * The grip that starts a drag. Put it on a small, obvious area of the row —
 * anywhere else stays tappable, which is the whole point of the handle.
 */
@Composable
fun ReorderHandle(
    state: ReorderState,
    /**
     * Stable identity of the row — the same value passed as its `key` in the
     * LazyColumn, so the state can find it again after a swap.
     */
    key: Any,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.wallets_reorder)
    Icon(
        imageVector = Icons.Filled.DragIndicator,
        contentDescription = label,
        tint = Broke.colors.fgSubtle,
        modifier = modifier
            .size(28.dp)
            .semantics { contentDescription = label }
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { state.onDragStart(key) },
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
