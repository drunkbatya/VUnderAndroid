package com.drunkbatya.vunder.feature.music

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.model.Playlist
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MusicRoute {
    data class Root(val userId: Long) : MusicRoute
    data object Saved : MusicRoute
    data object Listened : MusicRoute
    data class Recommendations(val userId: Long) : MusicRoute
    data class Playlists(val userId: Long) : MusicRoute
    data class PlaylistTracks(val playlist: Playlist) : MusicRoute
    data class Similar(val seed: Track) : MusicRoute
    data object Downloads : MusicRoute
}

enum class MusicPage(@StringRes val title: Int) {
    MyMusic(R.string.music_page_my_music),
    General(R.string.music_page_general),
}

enum class GeneralRow(@StringRes val title: Int) {
    Saved(R.string.music_general_saved),
    Playlists(R.string.music_general_playlists),
    Recommendations(R.string.music_general_recommendations),
    Listened(R.string.music_general_listened),
}

data class TrackReveal(val source: QueueSource, val track: Track)

@HiltViewModel
class MusicTabViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    downloads: DownloadCenter,
) : ViewModel() {
    val backStack = mutableStateListOf<MusicRoute>()

    var page by mutableStateOf(MusicPage.MyMusic)
        private set

    var reveal by mutableStateOf<TrackReveal?>(null)
        private set

    val generalRows: StateFlow<List<GeneralRow>> = settingsRepository.settings
        .map { settings -> GeneralRow.entries.filter { it != GeneralRow.Listened || settings[Toggle.History] } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeDownloads: StateFlow<Int> = downloads.jobs
        .map { jobs -> jobs.count { !it.state.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val root: MusicRoute.Root?
        get() = backStack.firstOrNull() as? MusicRoute.Root

    fun start(userId: Long) {
        if (root?.userId == userId) return
        backStack.clear()
        backStack += MusicRoute.Root(userId)
        page = MusicPage.MyMusic
        reveal = null
    }

    fun select(page: MusicPage) {
        this.page = page
    }

    fun push(route: MusicRoute) {
        if (backStack.lastOrNull() != route) backStack += route
    }

    fun showDownloads() = push(MusicRoute.Downloads)

    fun open(generalRow: GeneralRow) {
        val userId = root?.userId ?: return
        push(
            when (generalRow) {
                GeneralRow.Saved -> MusicRoute.Saved
                GeneralRow.Playlists -> MusicRoute.Playlists(userId)
                GeneralRow.Recommendations -> MusicRoute.Recommendations(userId)
                GeneralRow.Listened -> MusicRoute.Listened
            },
        )
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun open(source: QueueSource, track: Track) {
        val root = root ?: return
        val route = when (source) {
            QueueSource.MyMusic, is QueueSource.Search -> null
            QueueSource.Saved -> MusicRoute.Saved
            QueueSource.Listened -> MusicRoute.Listened
            is QueueSource.FromPlaylist -> MusicRoute.PlaylistTracks(source.playlist)
            QueueSource.Recommendations -> MusicRoute.Recommendations(root.userId)
            is QueueSource.Similar -> MusicRoute.Similar(source.seed)
            is QueueSource.Conversation -> return
        }
        Log.i(TAG, "open ${source.title} to reveal ${track.storageId}")
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        route?.let { backStack += it }
        page = if (route == null) MusicPage.MyMusic else MusicPage.General
        reveal = TrackReveal(source, track)
    }

    fun consumeReveal() {
        reveal = null
    }

    private companion object {
        const val TAG = "MusicTab"
    }
}
