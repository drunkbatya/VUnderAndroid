package com.drunkbatya.vunder.feature.music

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.cache.TrackExporter
import com.drunkbatya.vunder.core.model.LibraryMembership
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.feature.downloads.DownloadsScreen
import com.drunkbatya.vunder.ui.track.DeleteConfirmationDialog
import com.drunkbatya.vunder.ui.track.NoticeToasts
import com.drunkbatya.vunder.ui.track.TrackActionsViewModel
import com.drunkbatya.vunder.ui.track.TrackListActions
import com.drunkbatya.vunder.ui.track.TrackMenuItem

@Composable
fun MusicTab(
    userId: Long,
    trackMenuExtras: (Track) -> List<TrackMenuItem> = { emptyList() },
    navigator: MusicTabViewModel = hiltViewModel(),
    trackActions: TrackActionsViewModel = hiltViewModel(),
) {
    LaunchedEffect(userId) { navigator.start(userId) }
    NoticeToasts(trackActions.notices)
    val decorations by trackActions.decorations.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Track?>(null) }
    val saveToLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TrackExporter.MIME_TYPE)) { uri ->
        if (trackActions.finishSaveTo(uri)) navigator.showDownloads()
    }

    val listActions = TrackListActions(
        decorations = decorations,
        cover = trackActions::cover,
        onPlay = trackActions::play,
        menu = { track ->
            buildList {
                add(TrackMenuItem(R.string.track_play_next, Icons.AutoMirrored.Rounded.PlaylistPlay) { trackActions.playNext(track) })
                add(TrackMenuItem(R.string.track_add_to_queue, Icons.AutoMirrored.Rounded.PlaylistAdd) { trackActions.addToQueue(track) })
                add(TrackMenuItem(R.string.track_show_similar, Icons.Rounded.Radio) { navigator.push(MusicRoute.Similar(track)) })
                if (track.key in decorations.cachedKeys) {
                    add(TrackMenuItem(R.string.track_remove_offline, Icons.Rounded.Delete, destructive = true) { trackActions.removeOffline(track) })
                } else {
                    add(TrackMenuItem(R.string.download_kind_cache, Icons.Rounded.DownloadForOffline) { trackActions.saveOffline(track) })
                }
                if (track.isAvailable) {
                    add(TrackMenuItem(R.string.download_kind_music_folder, Icons.Rounded.Folder) {
                        trackActions.downloadToMusicFolder(track)
                        navigator.showDownloads()
                    })
                    add(TrackMenuItem(R.string.download_kind_save_to, Icons.Rounded.SaveAlt) {
                        trackActions.beginSaveTo(track)
                        saveToLauncher.launch("${TrackExporter.fileName(track)}.mp3")
                    })
                }
                addAll(trackMenuExtras(track))
                if (decorations.library.membership(track) == LibraryMembership.Mine) {
                    add(TrackMenuItem(R.string.track_delete_from_library, Icons.Rounded.RemoveCircleOutline, destructive = true) { deleting = track })
                } else if (track.isAvailable) {
                    add(TrackMenuItem(R.string.track_add_to_library, Icons.Rounded.AddCircleOutline) { trackActions.addToLibrary(track) })
                }
            }
        },
    )

    if (navigator.backStack.isNotEmpty()) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = navigator::back,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<MusicRoute.Root> { route -> MusicRootEntry(route.userId, navigator, listActions) }
                entry<MusicRoute.Saved> { StoredTracksEntry(QueueSource.Saved, navigator, listActions) }
                entry<MusicRoute.Listened> { StoredTracksEntry(QueueSource.Listened, navigator, listActions) }
                entry<MusicRoute.Recommendations> { route ->
                    RecommendationsEntry(QueueSource.Recommendations, route.userId, navigator, listActions)
                }
                entry<MusicRoute.Similar> { route ->
                    RecommendationsEntry(QueueSource.Similar(route.seed), userId, navigator, listActions)
                }
                entry<MusicRoute.PlaylistTracks> { route ->
                    val viewModel = hiltViewModel<PlaylistTracksViewModel, PlaylistTracksViewModel.Factory>(
                        creationCallback = { it.create(route.playlist) },
                    )
                    FilterableTrackListEntry(viewModel, navigator, listActions)
                }
                entry<MusicRoute.Playlists> { route ->
                    val viewModel = hiltViewModel<PlaylistsViewModel, PlaylistsViewModel.Factory>(
                        creationCallback = { it.create(route.userId) },
                    )
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    PlaylistsScreen(
                        state = state,
                        onQueryChange = viewModel::changeQuery,
                        onRefresh = viewModel::refresh,
                        onOpen = { navigator.push(MusicRoute.PlaylistTracks(it)) },
                        onBack = navigator::back,
                    )
                }
                entry<MusicRoute.Downloads> { DownloadsScreen(onBack = navigator::back) }
            },
        )
    }

    deleting?.let { track ->
        DeleteConfirmationDialog(track, onConfirm = { trackActions.deleteFromLibrary(track) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun MusicRootEntry(userId: Long, navigator: MusicTabViewModel, listActions: TrackListActions) {
    val viewModel = hiltViewModel<MyMusicViewModel, MyMusicViewModel.Factory>(creationCallback = { it.create(userId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val generalRows by navigator.generalRows.collectAsStateWithLifecycle()
    val activeDownloads by navigator.activeDownloads.collectAsStateWithLifecycle()
    val reveal = navigator.reveal
    LaunchedEffect(reveal) {
        if (reveal != null && (reveal.source == QueueSource.MyMusic || reveal.source is QueueSource.Search)) {
            viewModel.reveal(reveal.source, reveal.track)
            navigator.consumeReveal()
        }
    }
    NoticeToasts(viewModel.notices)
    MusicRootScreen(
        page = navigator.page,
        onSelectPage = navigator::select,
        activeDownloads = activeDownloads,
        onOpenDownloads = navigator::showDownloads,
    ) {
        when (navigator.page) {
            MusicPage.MyMusic -> MyMusicContent(
                state = state,
                actions = listActions,
                onQueryChange = viewModel::changeQuery,
                onSearch = viewModel::searchEverywhere,
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onRevealHandled = viewModel::revealHandled,
            )
            MusicPage.General -> GeneralContent(generalRows, onOpen = navigator::open)
        }
    }
}

@Composable
private fun StoredTracksEntry(source: QueueSource, navigator: MusicTabViewModel, listActions: TrackListActions) {
    val viewModel = hiltViewModel<StoredTracksViewModel, StoredTracksViewModel.Factory>(creationCallback = { it.create(source) })
    FilterableTrackListEntry(viewModel, navigator, listActions)
}

@Composable
private fun RecommendationsEntry(source: QueueSource, userId: Long, navigator: MusicTabViewModel, listActions: TrackListActions) {
    val viewModel = hiltViewModel<RecommendationsViewModel, RecommendationsViewModel.Factory>(
        creationCallback = { it.create(source, userId) },
    )
    FilterableTrackListEntry(viewModel, navigator, listActions, onRefresh = viewModel::load)
}

@Composable
private fun FilterableTrackListEntry(
    viewModel: FilterableTrackListViewModel,
    navigator: MusicTabViewModel,
    listActions: TrackListActions,
    onRefresh: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reveal = navigator.reveal
    LaunchedEffect(reveal) {
        if (reveal != null && reveal.source == viewModel.source) {
            viewModel.reveal(reveal.track)
            navigator.consumeReveal()
        }
    }
    NoticeToasts(viewModel.notices)
    FilterableTrackListScreen(
        title = viewModel.source.title,
        state = state,
        actions = listActions,
        onQueryChange = viewModel::changeQuery,
        onRevealHandled = viewModel::revealHandled,
        onBack = navigator::back,
        onRefresh = onRefresh,
    )
}
