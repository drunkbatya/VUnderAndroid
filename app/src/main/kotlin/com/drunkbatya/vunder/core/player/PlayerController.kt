package com.drunkbatya.vunder.core.player

import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.drunkbatya.vunder.core.cache.AudioCache
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.music.AudioApi
import com.drunkbatya.vunder.core.music.TrackLibrary
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.network.OfflineException
import com.drunkbatya.vunder.core.settings.RepeatMode
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@MainThread
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    streamingDataSource: OkHttpDataSource.Factory,
    private val audioApi: AudioApi,
    private val library: TrackLibrary,
    private val cache: AudioCache,
    private val settingsRepository: SettingsRepository,
    private val network: NetworkMonitor,
) {
    private val scope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate)

    internal val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setLooper(Looper.getMainLooper())
        .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, streamingDataSource)))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    internal val sessionPlayer: Player by lazy { SessionPlayer(this) }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private val queue = mutableListOf<Track>()
    private var order = mutableListOf<Int>()
    private var currentIndex: Int? = null
    private var source: QueueSource? = null
    private var status = PlaybackStatus.Idle
    private var shuffle = false
    private var repeatMode = RepeatMode.Off
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var loadedTrack: Track? = null
    private var retriedFreshUrl = false
    private var listenRecorded = false
    private var sessionConnection: ListenableFuture<MediaController>? = null

    private val current: Track?
        get() = currentIndex?.let(queue::getOrNull)

    private val orderPosition: Int?
        get() = currentIndex?.let { index -> order.indexOf(index).takeIf { it >= 0 } }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) trackReady()
            if (playbackState == Player.STATE_ENDED) trackFinished()
            syncStatus()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncStatus()
            tickProgress(isPlaying)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncStatus()

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = syncStatus()

        override fun onPlayerError(error: PlaybackException) {
            loadedTrack?.let { playbackFailed(it, error) }
        }
    }

    init {
        exoPlayer.addListener(listener)
        scope.launch {
            val settings = try {
                settingsRepository.current()
            } catch (failure: IOException) {
                Log.e(TAG, "settings load failed: ${failure.message}")
                return@launch
            }
            shuffle = settings[Toggle.Shuffle]
            repeatMode = settings.repeatMode
            publish()
        }
    }

    fun play(track: Track, tracks: List<Track>, source: QueueSource?) {
        queue.clear()
        queue += tracks
        this.source = source
        currentIndex = tracks.indexOf(track).takeIf { it >= 0 } ?: 0
        rebuildOrder()
        startCurrent()
    }

    fun playQueueItem(position: Int) {
        if (position !in order.indices) return
        currentIndex = order[position]
        startCurrent()
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        order.add(to, order.removeAt(from))
        Log.i(TAG, "queue move $from -> $to")
        publish()
    }

    fun removeQueueItem(position: Int) {
        if (position !in order.indices || order[position] == currentIndex) return
        val removedIndex = order.removeAt(position)
        queue.removeAt(removedIndex)
        order = order.map { if (it > removedIndex) it - 1 else it }.toMutableList()
        currentIndex?.let { if (it > removedIndex) currentIndex = it - 1 }
        Log.i(TAG, "queue remove at $position, left ${queue.size}")
        publish()
    }

    fun playNext(track: Track) = insertIntoQueue(track, afterCurrent = true)

    fun addToQueue(track: Track) = insertIntoQueue(track, afterCurrent = false)

    fun replaceQueue(tracks: List<Track>, source: QueueSource) {
        val currentTrack = current
        if (this.source != source || currentTrack == null) return
        val merged = tracks.toMutableList()
        for (track in queue) {
            if (merged.none { it.isSame(track) }) merged += track
        }
        queue.clear()
        queue += merged
        currentIndex = merged.indexOfFirst { it.isSame(currentTrack) }.takeIf { it >= 0 }
        if (currentTrack.isSame(loadedTrack)) loadedTrack = current
        rebuildOrder()
        Log.i(TAG, "queue replaced from ${source.title}, size ${merged.size}")
        publish()
    }

    fun appendToQueue(tracks: List<Track>, source: QueueSource) {
        if (this.source != source) return
        val fresh = tracks.filter { track -> queue.none { it.isSame(track) } }
        if (fresh.isEmpty()) return
        val indices = queue.size until queue.size + fresh.size
        queue += fresh
        order += if (shuffle) indices.shuffled() else indices.toList()
        Log.i(TAG, "queue appended ${fresh.size} from ${source.title}, size ${queue.size}")
        publish()
    }

    fun replace(track: Track, replacement: Track) {
        var replaced = false
        queue.indices.filter { queue[it].isSame(track) }.forEach {
            queue[it] = replacement
            replaced = true
        }
        if (!replaced) return
        if (track.isSame(loadedTrack)) loadedTrack = replacement
        Log.i(TAG, "queue replace ${track.storageId} -> ${replacement.storageId}")
        publish()
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        rebuildOrder()
        Log.i(TAG, "shuffle $shuffle")
        publish()
        scope.launch { settingsRepository.set(Toggle.Shuffle, shuffle) }
    }

    fun cycleRepeatMode() {
        repeatMode = repeatMode.next
        Log.i(TAG, "repeat $repeatMode")
        publish()
        scope.launch { settingsRepository.setRepeatMode(repeatMode) }
    }

    fun togglePlayPause() {
        when (status) {
            PlaybackStatus.Playing, PlaybackStatus.Loading -> pause()
            PlaybackStatus.Paused -> resume()
            PlaybackStatus.Idle -> startCurrent()
        }
    }

    fun pause() {
        exoPlayer.pause()
        setStatus(PlaybackStatus.Paused)
    }

    fun resume() {
        if (exoPlayer.currentMediaItem == null) {
            startCurrent()
            return
        }
        if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
        exoPlayer.play()
        setStatus(PlaybackStatus.Playing)
    }

    fun next() = advance(automatic = false)

    fun previous() {
        val position = orderPosition ?: return
        if (_progress.value.position > PREVIOUS_RESTART_THRESHOLD || position == 0) {
            seek(Duration.ZERO)
            return
        }
        currentIndex = order[position - 1]
        startCurrent()
    }

    fun seek(position: Duration) {
        exoPlayer.seekTo(position.inWholeMilliseconds)
        _progress.update { it.copy(position = position) }
    }

    fun stop() {
        loadJob?.cancel()
        loadedTrack = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _progress.value = PlaybackProgress()
        setStatus(PlaybackStatus.Idle)
    }

    private fun insertIntoQueue(track: Track, afterCurrent: Boolean) {
        val index = currentIndex
        val position = orderPosition
        if (index == null || position == null) {
            play(track, listOf(track), source = null)
            return
        }
        val existing = queue.indexOfFirst { it.isSame(track) }
        val existingPosition = order.indexOf(existing)
        if (existing >= 0 && existingPosition >= 0) {
            if (existing == index) return
            order.removeAt(existingPosition)
            order.add(if (afterCurrent) order.indexOf(index) + 1 else order.size, existing)
        } else {
            queue += track
            order.add(if (afterCurrent) position + 1 else order.size, queue.lastIndex)
        }
        Log.i(TAG, "queue ${if (afterCurrent) "play next" else "add"} ${track.storageId}, size ${queue.size}")
        publish()
    }

    private fun rebuildOrder() {
        val index = currentIndex
        order = if (shuffle && index != null && queue.isNotEmpty()) {
            (listOf(index) + queue.indices.filter { it != index }.shuffled()).toMutableList()
        } else {
            queue.indices.toMutableList()
        }
    }

    private fun advance(automatic: Boolean) {
        val position = orderPosition ?: return
        if (automatic && repeatMode == RepeatMode.One) {
            seek(Duration.ZERO)
            resume()
            return
        }
        var nextPosition = position + 1
        if (nextPosition >= order.size) {
            if (repeatMode != RepeatMode.All) {
                Log.i(TAG, "queue finished")
                stop()
                return
            }
            nextPosition = 0
        }
        currentIndex = order[nextPosition]
        startCurrent()
    }

    private fun startCurrent() {
        val track = current ?: return
        loadJob?.cancel()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        loadedTrack = track
        retriedFreshUrl = false
        listenRecorded = false
        _progress.value = PlaybackProgress(duration = track.duration.seconds)
        setStatus(PlaybackStatus.Loading)
        connectSession()
        Log.i(TAG, "start ${track.fullId} ${track.artist} - ${track.title}")
        loadJob = scope.launch {
            try {
                val uri = resolveUri(track)
                val playing = loadedTrack?.takeIf { it.isSame(current) } ?: return@launch
                if (uri == null) {
                    Log.e(TAG, "no url for ${track.fullId}, skipping")
                    next()
                    return@launch
                }
                load(uri, playing)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (offline: OfflineException) {
                Log.e(TAG, "url resolve failed for ${track.fullId}: ${offline.message}")
                nextCached()
            } catch (failure: Exception) {
                Log.e(TAG, "url resolve failed for ${track.fullId}: ${failure.message}")
                setStatus(PlaybackStatus.Paused)
            }
        }
    }

    private fun nextCached() {
        val position = orderPosition ?: return stop()
        val nextIndex = order.drop(position + 1).firstOrNull { cache.isCached(queue[it]) }
        if (nextIndex == null) {
            Log.i(TAG, "no cached tracks ahead in queue, stopping")
            stop()
            return
        }
        Log.i(TAG, "offline, skipping to cached track at $nextIndex")
        currentIndex = nextIndex
        startCurrent()
    }

    private suspend fun resolveUri(track: Track): Uri? {
        cache.localFile(track)?.let {
            Log.i(TAG, "playing local file for ${track.fullId}")
            return it.toUri()
        }
        if (!network.isConnected) throw OfflineException()
        track.url?.let { return it.toUri() }
        retriedFreshUrl = true
        return audioApi.freshUrl(track)?.toUri()
    }

    private fun load(uri: Uri, track: Track) {
        exoPlayer.setMediaItem(mediaItem(track, uri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun mediaItem(track: Track, uri: Uri): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(cache.localCover(track)?.toUri() ?: track.coverUrl?.toUri())
            .build()
        return MediaItem.Builder()
            .setMediaId(track.storageId)
            .setUri(uri)
            .setMimeType(if (uri.toString().contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun playbackFailed(track: Track, error: PlaybackException) {
        Log.e(TAG, "playback failed ${track.fullId}: ${error.errorCodeName} ${error.message}")
        if (!network.isConnected) {
            nextCached()
            return
        }
        if (retriedFreshUrl) {
            next()
            return
        }
        retriedFreshUrl = true
        loadJob = scope.launch {
            try {
                val url = audioApi.freshUrl(track)
                val playing = loadedTrack?.takeIf { it.isSame(current) } ?: return@launch
                if (url == null) next() else load(url.toUri(), playing)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "fresh url failed for ${track.fullId}: ${failure.message}")
                setStatus(PlaybackStatus.Paused)
            }
        }
    }

    private fun trackReady() {
        val track = loadedTrack ?: return
        if (!track.isSame(current)) return
        val duration = exoPlayer.duration
        if (duration != C.TIME_UNSET && duration > 0) _progress.update { it.copy(duration = duration.milliseconds) }
    }

    private fun trackFinished() {
        val track = loadedTrack ?: return
        if (!track.isSame(current)) return
        Log.i(TAG, "finished ${track.fullId}")
        advance(automatic = true)
    }

    private fun syncStatus() {
        val track = loadedTrack ?: return
        if (!track.isSame(current) || exoPlayer.currentMediaItem == null) return
        val suppressed = !exoPlayer.playWhenReady || exoPlayer.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
        when {
            exoPlayer.isPlaying -> {
                setStatus(PlaybackStatus.Playing)
                recordListenIfNeeded(track)
            }
            !suppressed && exoPlayer.playbackState == Player.STATE_BUFFERING -> setStatus(PlaybackStatus.Loading)
            suppressed && (status == PlaybackStatus.Playing || status == PlaybackStatus.Loading) -> setStatus(PlaybackStatus.Paused)
        }
    }

    private fun tickProgress(isPlaying: Boolean) {
        progressJob?.cancel()
        progressJob = null
        publishPosition()
        if (isPlaying) {
            progressJob = scope.launch {
                while (true) {
                    delay(PROGRESS_INTERVAL)
                    publishPosition()
                }
            }
        }
    }

    private fun publishPosition() {
        val track = loadedTrack ?: return
        if (!track.isSame(current)) return
        _progress.update { it.copy(position = exoPlayer.currentPosition.milliseconds) }
    }

    private fun recordListenIfNeeded(track: Track) {
        if (listenRecorded) return
        listenRecorded = true
        scope.launch {
            if (!settingsRepository.current()[Toggle.History]) return@launch
            try {
                library.recordListen(track)
            } catch (failure: SQLiteException) {
                Log.e(TAG, "listen record failed for ${track.fullId}: ${failure.message}")
            }
        }
    }

    private fun connectSession() {
        val connection = sessionConnection
        if (connection != null && (!connection.isDone || runCatching { connection.get().isConnected }.getOrDefault(false))) return
        connection?.let(MediaController::releaseFuture)
        sessionConnection = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync()
    }

    private fun setStatus(newStatus: PlaybackStatus) {
        status = newStatus
        publish()
    }

    private fun publish() {
        _state.value = PlayerState(
            orderedQueue = order.map(queue::get),
            currentOrderPosition = orderPosition,
            source = source,
            status = status,
            shuffle = shuffle,
            repeatMode = repeatMode,
        )
    }

    private companion object {
        const val TAG = "PlayerController"
        val PROGRESS_INTERVAL = 500.milliseconds
        val PREVIOUS_RESTART_THRESHOLD = 3.seconds
    }
}
