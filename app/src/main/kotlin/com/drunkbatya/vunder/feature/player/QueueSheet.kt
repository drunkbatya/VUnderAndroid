package com.drunkbatya.vunder.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.ui.track.TrackActionsViewModel
import com.drunkbatya.vunder.ui.track.TrackRow
import com.drunkbatya.vunder.ui.track.occurrenceKeys

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(viewModel: PlayerViewModel, trackActions: TrackActionsViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val decorations by trackActions.decorations.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val dragState = remember(listState) { QueueDragState(listState, viewModel::moveQueueItem) }
    val keys = remember(state.orderedQueue) { occurrenceKeys(state.orderedQueue) }

    LaunchedEffect(Unit) {
        state.currentOrderPosition?.let { listState.scrollToItem((it - 2).coerceAtLeast(0)) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Text(
            state.source?.title ?: stringResource(R.string.queue_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.orderedQueue, key = { index, _ -> keys[index] }) { index, track ->
                val isCurrent = index == state.currentOrderPosition
                val dragging = index == dragState.draggingIndex
                val dismissState = rememberSwipeToDismissBoxState()
                val currentIndex by rememberUpdatedState(index)
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    gesturesEnabled = !isCurrent && dragState.draggingIndex == null,
                    onDismiss = { viewModel.removeQueueItem(index) },
                    backgroundContent = {
                        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.queue_remove))
                            }
                        }
                    },
                    modifier = if (dragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = dragState.draggingOffset }
                    } else {
                        Modifier.animateItem()
                    },
                ) {
                    TrackRow(
                        track = track,
                        cover = trackActions.cover(track),
                        isCurrent = isCurrent,
                        isCached = track.key in decorations.cachedKeys,
                        modifier = Modifier
                            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
                            .clickable { viewModel.playQueueItem(index) },
                    ) {
                        Box(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .pointerInput(dragState) {
                                    detectDragGestures(
                                        onDragStart = { dragState.start(currentIndex) },
                                        onDragEnd = dragState::stop,
                                        onDragCancel = dragState::stop,
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragState.drag(amount.y)
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = stringResource(R.string.queue_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private class QueueDragState(private val listState: LazyListState, private val onMove: (Int, Int) -> Unit) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    private var initialOffset = 0
    private var draggedDistance by mutableFloatStateOf(0f)

    val draggingOffset: Float
        get() {
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingIndex } ?: return 0f
            return initialOffset + draggedDistance - item.offset
        }

    fun start(index: Int) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingIndex = index
        initialOffset = item.offset
        draggedDistance = 0f
    }

    fun drag(distance: Float) {
        val index = draggingIndex ?: return
        draggedDistance += distance
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val middle = item.offset + draggingOffset + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.index != index && middle.toInt() in it.offset..(it.offset + it.size)
        } ?: return
        onMove(index, target.index)
        draggingIndex = target.index
    }

    fun stop() {
        draggingIndex = null
        draggedDistance = 0f
    }
}
