package com.drunkbatya.vunder.core.session

import android.util.Log
import com.drunkbatya.vunder.core.cache.AudioCache
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.messaging.MessagesSync
import com.drunkbatya.vunder.core.messaging.VoicePlayer
import com.drunkbatya.vunder.core.music.TrackLibrary
import com.drunkbatya.vunder.core.network.PersistentCookieJar
import com.drunkbatya.vunder.core.player.PlayerController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SessionLifecycle @Inject constructor(
    private val sessionStore: SessionStore,
    private val messagesSync: MessagesSync,
    private val voicePlayer: VoicePlayer,
    private val player: PlayerController,
    private val downloads: DownloadCenter,
    private val cache: AudioCache,
    private val library: TrackLibrary,
    private val cookieJar: PersistentCookieJar,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch(Dispatchers.Main) {
            var signedInUserId: Long? = null
            sessionStore.session.map { it?.userId }.distinctUntilChanged().collect { userId ->
                if (userId != null) messagesSync.start() else signedInUserId?.let { clearAccountData(it) }
                signedInUserId = userId
            }
        }
    }

    private suspend fun clearAccountData(userId: Long) {
        Log.i(TAG, "session ended user_id=$userId, clearing account data")
        voicePlayer.stop()
        messagesSync.clear()
        player.stop()
        downloads.cancelAll()
        downloads.clearFinished()
        cache.clear()
        library.clear()
        withContext(Dispatchers.IO) { cookieJar.clear() }
    }

    private companion object {
        const val TAG = "SessionLifecycle"
    }
}
