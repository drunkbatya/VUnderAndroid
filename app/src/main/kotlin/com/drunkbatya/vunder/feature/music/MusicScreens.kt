package com.drunkbatya.vunder.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.Playlist
import com.drunkbatya.vunder.ui.track.CoverImage
import com.drunkbatya.vunder.ui.track.EmptyMessage
import com.drunkbatya.vunder.ui.track.FilterField
import com.drunkbatya.vunder.ui.track.TrackList
import com.drunkbatya.vunder.ui.track.TrackListActions
import com.drunkbatya.vunder.ui.track.resolve

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicRootScreen(
    page: MusicPage,
    onSelectPage: (MusicPage) -> Unit,
    activeDownloads: Int,
    onOpenDownloads: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SingleChoiceSegmentedButtonRow {
                        MusicPage.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = entry == page,
                                onClick = { onSelectPage(entry) },
                                shape = SegmentedButtonDefaults.itemShape(index, MusicPage.entries.size),
                                icon = {},
                            ) {
                                Text(stringResource(entry.title))
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        BadgedBox(badge = { if (activeDownloads > 0) Badge { Text(activeDownloads.toString()) } }) {
                            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.music_downloads))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
internal fun MyMusicContent(
    state: MyMusicUiState,
    actions: TrackListActions,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRevealHandled: (Boolean) -> Unit,
) {
    Column {
        FilterField(state.query, onQueryChange, R.string.music_search_hint, onSearch = onSearch)
        RefreshableBox(refreshing = state.refresh != null, onRefresh = onRefresh) {
            TrackList(
                state = state.list,
                actions = actions,
                onRevealHandled = onRevealHandled,
                onNearEnd = onLoadMore,
                header = state.refresh?.takeIf { it.total > 0 }?.let { refresh ->
                    { CaptionText(stringResource(R.string.music_refresh_progress, refresh.loaded, refresh.total)) }
                },
                footer = state.trackCount.takeIf { it > 0 }?.let { count ->
                    { CaptionText(pluralStringResource(R.plurals.music_track_count, count, count)) }
                },
            )
        }
    }
}

@Composable
internal fun GeneralContent(rows: List<GeneralRow>, onOpen: (GeneralRow) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(row.title)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpen(row) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun FilterableTrackListScreen(
    title: String,
    state: FilterableTrackListUiState,
    actions: TrackListActions,
    onQueryChange: (String) -> Unit,
    onRevealHandled: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            FilterField(state.query, onQueryChange, R.string.music_filter_hint)
            RefreshableBox(refreshing = state.loading, onRefresh = onRefresh) {
                TrackList(state.list, actions, onRevealHandled)
            }
        }
    }
}

@Composable
internal fun PlaylistsScreen(
    state: PlaylistsUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.music_general_playlists), onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            FilterField(state.query, onQueryChange, R.string.music_filter_hint)
            RefreshableBox(refreshing = state.loading, onRefresh = onRefresh) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.playlists, key = { "${it.ownerId}_${it.id}" }) { playlist ->
                        ListItem(
                            leadingContent = { CoverImage(playlist.coverUrl, size = 48.dp) },
                            headlineContent = { Text(playlist.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.music_track_count, playlist.trackCount, playlist.trackCount))
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable { onOpen(playlist) },
                        )
                    }
                }
                val message = state.emptyMessage
                if (state.playlists.isEmpty() && message != null) EmptyMessage(message.resolve())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.music_back))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableBox(refreshing: Boolean, onRefresh: (() -> Unit)?, content: @Composable BoxScope.() -> Unit) {
    if (onRefresh == null) {
        Box(Modifier.fillMaxSize(), content = content)
    } else {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
