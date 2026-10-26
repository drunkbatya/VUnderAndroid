package com.drunkbatya.vunder.feature.music

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.music.AudioApi
import com.drunkbatya.vunder.core.music.TrackLibrary
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.ui.track.TrackListUiState
import com.drunkbatya.vunder.ui.track.TrackSection
import com.drunkbatya.vunder.ui.track.UiText
import com.drunkbatya.vunder.ui.components.trimmedText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryRefresh(val loaded: Int = 0, val total: Int = 0)

data class MyMusicUiState(
    val refresh: LibraryRefresh? = null,
    val trackCount: Int = 0,
    val list: TrackListUiState = TrackListUiState(),
)

private data class GlobalSearch(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val exhausted: Boolean = false,
    val searching: Boolean = false,
)

@HiltViewModel(assistedFactory = MyMusicViewModel.Factory::class)
class MyMusicViewModel @AssistedInject constructor(
    @Assisted private val ownerId: Long,
    private val audioApi: AudioApi,
    private val library: TrackLibrary,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val libraryTracks = MutableStateFlow<List<Track>?>(null)
    val searchField = TextFieldState()
    private val search = MutableStateFlow(GlobalSearch())
    private val refresh = MutableStateFlow<LibraryRefresh?>(null)
    private val pendingReveal = MutableStateFlow<Track?>(null)
    private var searchJob: Job? = null
    private var refreshJob: Job? = null

    private val noticeChannel = Channel<UiText>(Channel.BUFFERED)
    val notices: Flow<UiText> = noticeChannel.receiveAsFlow()

    val state: StateFlow<MyMusicUiState> = combine(
        libraryTracks,
        searchField.trimmedText(),
        search,
        refresh,
        pendingReveal,
    ) { tracks, trimmed, search, refresh, reveal ->
        MyMusicUiState(
            refresh = refresh,
            trackCount = if (trimmed.isEmpty()) tracks.orEmpty().size else 0,
            list = if (trimmed.isEmpty()) {
                TrackListUiState(
                    sections = listOf(TrackSection(title = null, tracks = tracks.orEmpty(), source = QueueSource.MyMusic)),
                    emptyMessage = tracks?.let { UiText.Resource(R.string.music_empty_library) },
                    reveal = reveal.takeIf { tracks != null },
                )
            } else {
                searchList(tracks.orEmpty(), trimmed, search, reveal)
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyMusicUiState())

    init {
        viewModelScope.launch {
            library.tracks.collect { tracks ->
                val firstLoad = libraryTracks.value == null
                libraryTracks.value = tracks
                if (!firstLoad) return@collect
                Log.i(TAG, "saved library loaded tracks=${tracks.size}")
                if (tracks.isEmpty() && network.isConnected) refresh()
            }
        }
    }

    fun searchEverywhere() {
        val trimmed = searchField.text.trim().toString()
        if (trimmed.isEmpty()) return
        if (!network.isConnected) {
            pendingReveal.value = null
            noticeChannel.trySend(UiText.Resource(R.string.music_no_internet))
            return
        }
        searchJob?.cancel()
        search.value = GlobalSearch(query = trimmed)
        loadSearchPage()
    }

    fun loadMore() {
        val current = search.value
        if (current.query.isEmpty() || current.query != searchField.text.trim().toString() || current.exhausted || current.searching) return
        loadSearchPage()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        if (!network.isConnected) {
            noticeChannel.trySend(UiText.Resource(R.string.music_no_internet))
            return
        }
        refresh.value = LibraryRefresh()
        refreshJob = viewModelScope.launch {
            try {
                val loaded = audioApi.allTracks(ownerId) { count, total -> refresh.value = LibraryRefresh(count, total) }
                library.replace(loaded)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "library refresh failed: ${failure.message}")
                noticeChannel.send(UiText.failure(failure))
            } finally {
                refresh.value = null
            }
        }
    }

    fun reveal(source: QueueSource, track: Track) {
        if (source is QueueSource.Search) {
            searchField.setTextAndPlaceCursorAtEnd(source.query)
            pendingReveal.value = track
            searchEverywhere()
        } else {
            searchField.clearText()
            pendingReveal.value = track
        }
    }

    fun revealHandled(found: Boolean) {
        pendingReveal.value = null
        if (!found) noticeChannel.trySend(UiText.Resource(R.string.music_track_not_in_list))
    }

    private fun loadSearchPage() {
        val searched = search.value.query
        val offset = search.value.results.size
        search.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            try {
                val page = audioApi.search(searched, offset)
                search.update {
                    it.copy(results = it.results + page, exhausted = page.size < AudioApi.SEARCH_PAGE_SIZE, searching = false)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "search failed: ${failure.message}")
                search.update { it.copy(searching = false) }
                noticeChannel.send(UiText.failure(failure))
            }
        }
    }

    private fun searchList(tracks: List<Track>, query: String, search: GlobalSearch, reveal: Track?): TrackListUiState {
        val lowered = query.lowercase()
        val local = tracks.filter { it.matches(lowered) }
        val searched = search.query == query
        val sections = buildList {
            if (local.isNotEmpty()) add(TrackSection(R.string.music_section_my_music, local, QueueSource.MyMusic))
            if (searched && search.results.isNotEmpty()) {
                add(TrackSection(R.string.music_section_search_results, search.results, QueueSource.Search(query)))
            }
        }
        val emptyMessage = when {
            searched && search.searching -> UiText.Resource(R.string.music_searching)
            searched -> UiText.Resource(R.string.music_nothing_found_for, listOf(query))
            network.isConnected -> UiText.Resource(R.string.music_no_local_matches_online)
            else -> UiText.Resource(R.string.music_no_local_matches_offline)
        }
        return TrackListUiState(sections, emptyMessage, reveal.takeIf { searched && !search.searching })
    }

    @AssistedFactory
    interface Factory {
        fun create(ownerId: Long): MyMusicViewModel
    }

    private companion object {
        const val TAG = "MyMusic"
    }
}
