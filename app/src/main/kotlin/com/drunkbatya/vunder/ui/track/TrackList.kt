package com.drunkbatya.vunder.ui.track

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.model.TrackKey
import com.drunkbatya.vunder.ui.components.MenuItem
import com.drunkbatya.vunder.ui.components.LoadMoreEffect
import com.drunkbatya.vunder.ui.components.StatusText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TrackSection(@StringRes val title: Int?, val tracks: List<Track>, val source: QueueSource)

data class TrackListUiState(
    val sections: List<TrackSection> = emptyList(),
    val emptyMessage: UiText? = null,
    val reveal: Track? = null,
) {
    val isEmpty: Boolean
        get() = sections.all { it.tracks.isEmpty() }
}

class TrackListActions(
    val decorations: TrackDecorations,
    val cover: (Track) -> Any?,
    val onPlay: (Track, List<Track>, QueueSource) -> Unit,
    val menu: (Track) -> List<MenuItem>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackList(
    state: TrackListUiState,
    actions: TrackListActions,
    onRevealHandled: (found: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNearEnd: () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var highlighted by remember { mutableStateOf<TrackKey?>(null) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    val sectionKeys = remember(state.sections) {
        state.sections.mapIndexed { sectionIndex, section -> occurrenceKeys(section.tracks).map { "$sectionIndex/$it" } }
    }

    LaunchedEffect(state.reveal) {
        val track = state.reveal ?: return@LaunchedEffect
        val index = state.listIndexOf(track, headerCount = if (header != null) 1 else 0)
        if (index == null) {
            onRevealHandled(false)
            return@LaunchedEffect
        }
        listState.animateScrollToItem(index, -listState.layoutInfo.viewportSize.height / 3)
        highlighted = track.key
        delay(REVEAL_HIGHLIGHT_MILLIS)
        highlighted = null
        onRevealHandled(true)
    }

    LoadMoreEffect(listState, threshold = NEAR_END_ITEMS, retryKey = state.sections, onLoadMore = onNearEnd)
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > listState.layoutInfo.visibleItemsInfo.size }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            header?.let { item { it() } }
            state.sections.forEachIndexed { sectionIndex, section ->
                section.title?.let { title -> item { SectionHeader(stringResource(title)) } }
                val keys = sectionKeys[sectionIndex]
                itemsIndexed(section.tracks, key = { row, _ -> keys[row] }) { _, track ->
                    TrackRow(
                        track = track,
                        cover = actions.cover(track),
                        isCurrent = track.key == actions.decorations.current,
                        isCached = track.key in actions.decorations.cachedKeys,
                        modifier = Modifier
                            .background(if (track.key == highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { actions.onPlay(track, section.tracks, section.source) },
                                onLongClick = { menuTrack = track },
                            ),
                    ) {
                        IconButton(onClick = { menuTrack = track }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.track_more))
                        }
                    }
                }
            }
            footer?.let { item { it() } }
        }
        val message = state.emptyMessage
        if (state.isEmpty && message != null) StatusText(message.resolve())
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.music_scroll_to_top))
            }
        }
    }

    menuTrack?.let { track ->
        TrackMenuSheet(track, actions.cover(track), actions.menu(track), onDismiss = { menuTrack = null })
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun TrackListUiState.listIndexOf(track: Track, headerCount: Int): Int? {
    var index = headerCount
    for (section in sections) {
        if (section.title != null) index++
        val row = section.tracks.indexOfFirst { it.isSame(track) }
        if (row >= 0) return index + row
        index += section.tracks.size
    }
    return null
}

fun occurrenceKeys(tracks: List<Track>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return tracks.map { track ->
        val occurrence = seen.merge(track.storageId, 1, Int::plus) ?: 1
        "${track.storageId}#$occurrence"
    }
}

private const val REVEAL_HIGHLIGHT_MILLIS = 1000L
private const val NEAR_END_ITEMS = 20
