package com.drunkbatya.vunder.feature.music

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.Playlist
import com.drunkbatya.vunder.core.music.AudioApi
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.ui.track.UiText
import com.drunkbatya.vunder.ui.components.trimmedText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val loading: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val emptyMessage: UiText? = null,
)

private data class PlaylistsLoad(
    val playlists: List<Playlist>? = null,
    val loading: Boolean = false,
    val failure: UiText? = null,
)

@HiltViewModel(assistedFactory = PlaylistsViewModel.Factory::class)
class PlaylistsViewModel @AssistedInject constructor(
    @Assisted private val ownerId: Long,
    private val audioApi: AudioApi,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val load = MutableStateFlow(PlaylistsLoad())
    val filterField = TextFieldState()

    val state: StateFlow<PlaylistsUiState> = combine(load, filterField.trimmedText()) { load, query ->
        val lowered = query.lowercase()
        val all = load.playlists.orEmpty()
        val shown = if (lowered.isEmpty()) all else all.filter { it.title.lowercase().contains(lowered) }
        PlaylistsUiState(
            loading = load.loading,
            playlists = shown,
            emptyMessage = when {
                all.isNotEmpty() -> UiText.Resource(R.string.music_nothing_matches, listOf(query))
                load.loading -> UiText.Resource(R.string.music_loading)
                load.failure != null -> load.failure
                load.playlists != null -> UiText.Resource(R.string.music_no_playlists)
                else -> UiText.Resource(R.string.music_no_internet)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistsUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (load.value.loading || !network.isConnected) return
        load.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            try {
                val playlists = audioApi.playlists(ownerId)
                load.value = PlaylistsLoad(playlists = playlists)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "playlists failed: ${failure.message}")
                load.update { it.copy(loading = false, failure = UiText.failure(failure)) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(ownerId: Long): PlaylistsViewModel
    }

    private companion object {
        const val TAG = "Playlists"
    }
}
