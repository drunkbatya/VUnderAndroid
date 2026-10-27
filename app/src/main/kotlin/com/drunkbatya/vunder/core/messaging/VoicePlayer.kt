package com.drunkbatya.vunder.core.messaging

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.drunkbatya.vunder.core.player.PlayerController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoicePlaybackState(val current: VoiceMessage? = null, val isPlaying: Boolean = false)

@OptIn(UnstableApi::class)
@MainThread
@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamingDataSource: OkHttpDataSource.Factory,
    private val musicPlayer: PlayerController,
) {
    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setLooper(Looper.getMainLooper())
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(streamingDataSource),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .build()
            .apply { addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _state.update { if (it.current == null) it else it.copy(isPlaying = playWhenReady) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) stop()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "voice ${_state.value.current?.storageId} failed: ${error.errorCodeName} ${error.message}")
            stop()
        }
    }

    fun toggle(voice: VoiceMessage) {
        if (_state.value.current == voice) {
            if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
            return
        }
        val url = voice.mp3Url
        if (url == null) {
            Log.e(TAG, "voice ${voice.storageId} has no url")
            return
        }
        stop()
        if (musicPlayer.state.value.isPlaying) musicPlayer.pause()
        Log.i(TAG, "voice play ${voice.storageId}")
        _state.value = VoicePlaybackState(voice, isPlaying = true)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun stop() {
        if (_state.value.current == null) return
        _state.value = VoicePlaybackState()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    private companion object {
        const val TAG = "VoicePlayer"
    }
}
