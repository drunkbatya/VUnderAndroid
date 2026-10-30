package com.drunkbatya.vunder.ui.track

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.cache.AudioCache
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.cache.DownloadKind
import com.drunkbatya.vunder.core.messaging.Outbox
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.model.TrackKey
import com.drunkbatya.vunder.core.music.LibraryEditor
import com.drunkbatya.vunder.core.music.LibrarySnapshot
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackDecorations(
    val current: TrackKey?,
    val cachedKeys: Set<TrackKey>,
    val library: LibrarySnapshot,
)

@HiltViewModel
class TrackActionsViewModel @Inject constructor(
    private val player: PlayerController,
    private val editor: LibraryEditor,
    private val cache: AudioCache,
    private val downloads: DownloadCenter,
    private val network: NetworkMonitor,
    private val outbox: Outbox,
) : ViewModel() {
    val decorations: StateFlow<TrackDecorations> = combine(
        player.state.map { it.current?.key }.distinctUntilChanged(),
        cache.cachedKeys,
        editor.snapshot,
        ::TrackDecorations,
    ).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TrackDecorations(player.state.value.current?.key, cache.cachedKeys.value, editor.snapshot.value),
    )

    private val noticeChannel = Channel<UiText>(Channel.BUFFERED)
    val notices: Flow<UiText> = noticeChannel.receiveAsFlow()

    fun cover(track: Track): Any? = cache.localCover(track) ?: track.coverUrl

    fun play(track: Track, tracks: List<Track>, source: QueueSource) = player.play(track, tracks, source)

    fun playNext(track: Track) = player.playNext(track)

    fun addToQueue(track: Track) = player.addToQueue(track)

    fun saveOffline(track: Track) = downloads.enqueue(track, DownloadKind.Cache)

    fun removeOffline(track: Track) {
        Log.i(TAG, "remove offline requested for ${track.storageId}")
        downloads.cancelCacheJobs(track)
        viewModelScope.launch { cache.remove(track) }
    }

    fun downloadToMusicFolder(track: Track) = downloads.enqueue(track, DownloadKind.MusicFolder)

    fun send(track: Track, peerId: Long, title: String) {
        outbox.enqueue(peerId, text = "", attachments = listOf(track))
        noticeChannel.trySend(UiText.Resource(if (network.isConnected) R.string.music_sent_to else R.string.music_will_send_to, listOf(title)))
    }

    fun addToLibrary(track: Track) = editLibrary(track, delete = false)

    fun deleteFromLibrary(track: Track) = editLibrary(track, delete = true)

    private fun editLibrary(track: Track, delete: Boolean) {
        if (!network.isConnected) {
            noticeChannel.trySend(UiText.Resource(R.string.music_no_internet))
            return
        }
        viewModelScope.launch {
            try {
                if (delete) editor.delete(track) else editor.add(track)
                noticeChannel.send(UiText.Resource(if (delete) R.string.music_deleted else R.string.music_added))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "${if (delete) "delete" else "add"} ${track.fullId} failed: ${failure.message}")
                noticeChannel.send(UiText.failure(failure))
            }
        }
    }

    private companion object {
        const val TAG = "TrackActions"
    }
}
