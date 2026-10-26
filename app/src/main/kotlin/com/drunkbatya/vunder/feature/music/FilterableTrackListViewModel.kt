package com.drunkbatya.vunder.feature.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.ui.track.TrackListUiState
import com.drunkbatya.vunder.ui.track.TrackSection
import com.drunkbatya.vunder.ui.track.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class FilterableTrackListUiState(
    val query: String = "",
    val loading: Boolean = false,
    val list: TrackListUiState = TrackListUiState(),
)

abstract class FilterableTrackListViewModel(val source: QueueSource) : ViewModel() {
    protected val tracks = MutableStateFlow<List<Track>?>(null)
    protected val loading = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val pendingReveal = MutableStateFlow<Track?>(null)

    private val noticeChannel = Channel<UiText>(Channel.BUFFERED)
    val notices: Flow<UiText> = noticeChannel.receiveAsFlow()

    val state: StateFlow<FilterableTrackListUiState> = combine(tracks, loading, query, pendingReveal) { tracks, loading, query, reveal ->
        val trimmed = query.trim().lowercase()
        val shown = if (trimmed.isEmpty()) tracks.orEmpty() else tracks.orEmpty().filter { it.matches(trimmed) }
        FilterableTrackListUiState(
            query = query,
            loading = loading,
            list = TrackListUiState(
                sections = listOf(TrackSection(title = null, tracks = shown, source = source)),
                emptyMessage = when {
                    !tracks.isNullOrEmpty() -> UiText.Resource(R.string.music_nothing_matches, listOf(query.trim()))
                    loading -> UiText.Resource(R.string.music_loading)
                    else -> emptyMessage(loaded = tracks != null)
                },
                reveal = reveal.takeIf { tracks != null && !loading },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterableTrackListUiState())

    protected abstract fun emptyMessage(loaded: Boolean): UiText?

    fun changeQuery(query: String) {
        this.query.value = query
    }

    fun reveal(track: Track) {
        query.value = ""
        pendingReveal.value = track
    }

    fun revealHandled(found: Boolean) {
        pendingReveal.value = null
        if (!found) notify(UiText.Resource(R.string.music_track_not_in_list))
    }

    protected fun notify(notice: UiText) {
        noticeChannel.trySend(notice)
    }
}
