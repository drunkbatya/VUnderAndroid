package com.drunkbatya.vunder.core.cache

import android.util.Log
import androidx.annotation.MainThread
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.player.PlaybackProgress
import com.drunkbatya.vunder.core.player.PlaybackStatus
import com.drunkbatya.vunder.core.player.PlayerController
import com.drunkbatya.vunder.core.session.SessionStore
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Singleton
class AutoCacheController @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val player: PlayerController,
    private val downloads: DownloadCenter,
    private val cache: AudioCache,
    private val settingsRepository: SettingsRepository,
    private val sessionStore: SessionStore,
    private val network: NetworkMonitor,
) {
    private var job: Job? = null
    private var triggeredTrack: Track? = null

    @MainThread
    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.Main.immediate) { player.progress.collect(::progressChanged) }
    }

    private suspend fun progressChanged(progress: PlaybackProgress) {
        val state = player.state.value
        val track = state.current ?: return
        if (state.status != PlaybackStatus.Playing || track.isSame(triggeredTrack)) return
        val threshold = if (network.connectivity.value.isMetered) METERED_THRESHOLD else UNMETERED_THRESHOLD
        if (progress.position < threshold || !network.isConnected) return
        triggeredTrack = track
        listOfNotNull(track, state.upcoming)
            .filter { shouldCache(it) && !cache.isCached(it) }
            .forEach {
                Log.i(TAG, "auto-cache ${it.storageId}")
                downloads.enqueue(it, DownloadKind.Cache)
            }
    }

    private suspend fun shouldCache(track: Track): Boolean {
        val settings = settingsRepository.current()
        return settings[Toggle.Cache] && (track.ownerId == sessionStore.current()?.userId || !settings[Toggle.CacheOnlyMine])
    }

    private companion object {
        const val TAG = "AutoCacheController"
        val METERED_THRESHOLD = 30.seconds
        val UNMETERED_THRESHOLD = 10.seconds
    }
}
