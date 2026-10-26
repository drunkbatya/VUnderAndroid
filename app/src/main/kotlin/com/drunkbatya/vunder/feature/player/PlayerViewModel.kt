package com.drunkbatya.vunder.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.cache.DownloadKind
import com.drunkbatya.vunder.core.cache.TrackExporter
import com.drunkbatya.vunder.core.player.PlaybackProgress
import com.drunkbatya.vunder.core.player.PlayerController
import com.drunkbatya.vunder.core.player.PlayerState
import com.drunkbatya.vunder.core.player.TrackEncoding
import com.drunkbatya.vunder.core.player.TrackEncodingProbe
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: PlayerController,
    encodingProbe: TrackEncodingProbe,
    downloads: DownloadCenter,
    exporter: TrackExporter,
) : ViewModel() {
    val state: StateFlow<PlayerState> = player.state
    val progress: StateFlow<PlaybackProgress> = player.progress

    @OptIn(ExperimentalCoroutinesApi::class)
    val encoding: StateFlow<TrackEncoding?> = player.state
        .map { it.current }
        .distinctUntilChanged { old, new -> old?.key == new?.key }
        .transformLatest { track ->
            emit(null)
            if (track != null) emit(encodingProbe.probe(track))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val canDownload: StateFlow<Boolean> = combine(
        player.state.map { it.current }.distinctUntilChanged { old, new -> old?.key == new?.key },
        downloads.jobs,
    ) { track, jobs ->
        val trackJobs = jobs.filter { it.kind == DownloadKind.MusicFolder && track != null && it.track.isSame(track) }
        Triple(track, trackJobs.any { !it.state.isFinished }, trackJobs.count { it.state.isFinished })
    }
        .distinctUntilChanged { old, new -> old.first?.key == new.first?.key && old.second == new.second && old.third == new.third }
        .mapLatest { (track, downloading) -> track != null && !downloading && !exporter.isInMusicFolder(track) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), false)

    fun togglePlayPause() = player.togglePlayPause()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seek(position: Duration) = player.seek(position)

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeatMode() = player.cycleRepeatMode()

    fun playQueueItem(position: Int) = player.playQueueItem(position)

    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)

    fun removeQueueItem(position: Int) = player.removeQueueItem(position)
}
