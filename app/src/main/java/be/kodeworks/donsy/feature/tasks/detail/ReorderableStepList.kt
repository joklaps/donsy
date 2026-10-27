// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.tasks.detail

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/** Lets a step row start a drag; attach [dragHandle] to the row's handle. */
class StepDragScope internal constructor(val dragHandle: Modifier, val isDragging: Boolean)

/**
 * A short, non-lazy list of steps that can be reordered by dragging a handle.
 * A drag starts only after a long press on the handle, so a swipe that happens to start there still scrolls the screen.
 * The order is changed locally while dragging and reported once, via [onMove], when the drag ends.
 */
@Composable
fun ReorderableStepList(
    steps: List<StepItem>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (step: StepItem, index: Int, drag: StepDragScope) -> Unit,
) {
    var order by remember(steps) { mutableStateOf(steps) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }
    val currentOnMove by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current

    fun onDrag(dy: Float) {
        val id = draggedId ?: return
        offset += dy
        val index = order.indexOfFirst { it.id == id }
        // Swap with a neighbour once the dragged row has moved past half of it.
        if (offset > 0 && index < order.lastIndex) {
            val next = heights[order[index + 1].id] ?: return
            if (offset > next / 2f) {
                order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                offset -= next
            }
        } else if (offset < 0 && index > 0) {
            val previous = heights[order[index - 1].id] ?: return
            if (-offset > previous / 2f) {
                order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                offset += previous
            }
        }
    }

    fun onDragEnd() {
        val id = draggedId ?: return
        val endIndex = order.indexOfFirst { it.id == id }
        if (endIndex != dragStartIndex) currentOnMove(dragStartIndex, endIndex)
        draggedId = null
        offset = 0f
    }

    Column(modifier) {
        order.forEachIndexed { index, step ->
            key(step.id) {
                val isDragging = draggedId == step.id
                val handle = Modifier.pointerInput(step.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggedId = step.id
                            dragStartIndex = order.indexOfFirst { it.id == step.id }
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                    )
                }
                Column(
                    Modifier
                        .onSizeChanged { heights[step.id] = it.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) offset else 0f },
                ) {
                    row(step, index, StepDragScope(handle, isDragging))
                }
            }
        }
    }
}
