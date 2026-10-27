package com.drunkbatya.vunder.core.music

import android.util.Log
import com.drunkbatya.vunder.core.cache.AudioCache
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.LibraryMembership
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.model.TrackKey
import com.drunkbatya.vunder.core.network.VkApiException
import com.drunkbatya.vunder.core.player.PlayerController
import com.drunkbatya.vunder.core.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LibrarySnapshot(val userId: Long?, val trackKeys: Set<TrackKey>?) {
    fun membership(track: Track): LibraryMembership = when {
        track.ownerId != userId -> LibraryMembership.Foreign
        trackKeys == null || track.key in trackKeys -> LibraryMembership.Mine
        else -> LibraryMembership.Removed
    }
}

@Singleton
class LibraryEditor @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    sessionStore: SessionStore,
    private val audioApi: AudioApi,
    private val library: TrackLibrary,
    private val player: PlayerController,
    private val cache: AudioCache,
    private val downloads: DownloadCenter,
) {
    val snapshot: StateFlow<LibrarySnapshot> = combine(sessionStore.session, library.libraryKeys) { session, keys ->
        LibrarySnapshot(session?.userId, keys)
    }.stateIn(scope, SharingStarted.Eagerly, LibrarySnapshot(userId = null, trackKeys = null))

    fun membership(track: Track): LibraryMembership = snapshot.value.membership(track)

    suspend fun add(track: Track): Track = inApplicationScope {
        if (membership(track) == LibraryMembership.Removed) restore(track) else addCopy(track)
    }

    suspend fun delete(track: Track): Unit = inApplicationScope {
        audioApi.delete(track)
        downloads.cancelCacheJobs(track)
        if (cache.isCached(track)) cache.remove(track)
        library.removeFromLibrary(track)
    }

    private suspend fun addCopy(track: Track): Track {
        val userId = snapshot.value.userId ?: throw VkApiException.SessionExpired()
        val newId = audioApi.add(track)
        val added = track.copy(ownerId = userId, id = newId, accessKey = null)
        library.insertAtTop(added)
        player.replace(track, added)
        return added
    }

    private suspend fun restore(track: Track): Track {
        try {
            audioApi.restore(track)
        } catch (rejection: VkApiException.Api) {
            Log.e(TAG, "restore ${track.storageId} rejected ${rejection.error.code}: ${rejection.error.message}, adding instead")
            return addCopy(track)
        }
        library.insertAtTop(track)
        return track
    }

    private suspend fun <T> inApplicationScope(block: suspend () -> T): T =
        scope.async(Dispatchers.Main.immediate) { block() }.await()

    private companion object {
        const val TAG = "LibraryEditor"
    }
}
