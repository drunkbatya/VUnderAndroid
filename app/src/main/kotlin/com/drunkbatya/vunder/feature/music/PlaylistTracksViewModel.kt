package com.drunkbatya.vunder.feature.music

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.Playlist
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.music.AudioApi
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.ui.track.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlaylistTracksViewModel.Factory::class)
class PlaylistTracksViewModel @AssistedInject constructor(
    @Assisted private val playlist: Playlist,
    private val audioApi: AudioApi,
    private val network: NetworkMonitor,
) : FilterableTrackListViewModel(QueueSource.FromPlaylist(playlist)) {
    init {
        load()
    }

    override fun emptyMessage(loaded: Boolean): UiText? = when {
        !network.isConnected -> UiText.Resource(R.string.music_no_internet)
        loaded -> UiText.Resource(R.string.music_playlist_empty)
        else -> null
    }

    private fun load() {
        if (!network.isConnected) return
        loading.value = true
        viewModelScope.launch {
            try {
                tracks.value = audioApi.allTracks(playlist.ownerId, playlist) { _, _ -> }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "playlist ${playlist.id} failed: ${failure.message}")
                notify(UiText.failure(failure))
            } finally {
                loading.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(playlist: Playlist): PlaylistTracksViewModel
    }

    private companion object {
        const val TAG = "PlaylistTracks"
    }
}
