package com.drunkbatya.vunder.core.music

import android.util.Log
import com.drunkbatya.vunder.core.database.TrackDao
import com.drunkbatya.vunder.core.database.TrackEntity
import com.drunkbatya.vunder.core.database.toEntity
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.model.TrackKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TrackLibrary @Inject constructor(private val trackDao: TrackDao) {
    val tracks: Flow<List<Track>> = trackDao.library().map { it.map(TrackEntity::toTrack) }
    val saved: Flow<List<Track>> = trackDao.saved().map { it.map(TrackEntity::toTrack) }
    val listened: Flow<List<Track>> = trackDao.listened().map { it.map(TrackEntity::toTrack) }
    val libraryKeys: Flow<Set<TrackKey>> = trackDao.libraryKeys().map { it.toSet() }
    val cachedKeys: Flow<Set<TrackKey>> = trackDao.cachedKeys().map { it.toSet() }

    suspend fun replace(tracks: List<Track>) {
        trackDao.replaceLibrary(tracks.map { it.toEntity() })
        Log.i(TAG, "library replaced with ${tracks.size} tracks")
    }

    suspend fun insertAtTop(track: Track) {
        trackDao.insertAtTop(track.toEntity())
        Log.i(TAG, "library insert at top ${track.storageId}")
    }

    suspend fun removeFromLibrary(track: Track) {
        trackDao.removeFromLibrary(track.key)
        Log.i(TAG, "library remove ${track.storageId}")
    }

    suspend fun currentCachedKeys(): Set<TrackKey> = trackDao.currentCachedKeys().toSet()

    suspend fun markCached(track: Track, cachedAt: Long?) = trackDao.markCached(track.toEntity(), cachedAt)

    suspend fun unmarkCached(keys: Collection<TrackKey>) = trackDao.unmarkCached(keys.toList())

    suspend fun clearCacheMarks() = trackDao.clearCacheMarks()

    suspend fun recordListen(track: Track) = trackDao.recordListen(track.toEntity(), System.currentTimeMillis())

    suspend fun clear() {
        val deleted = trackDao.clear()
        Log.i(TAG, "library cleared, removed $deleted tracks")
    }

    private companion object {
        const val TAG = "TrackLibrary"
    }
}
