package com.drunkbatya.vunder.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.drunkbatya.vunder.core.model.TrackKey
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TrackDao {
    @Query("SELECT * FROM track WHERE libraryPosition IS NOT NULL ORDER BY libraryPosition")
    abstract fun library(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE cachedAt IS NOT NULL ORDER BY cachedAt DESC")
    abstract fun saved(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT track.* FROM track
        JOIN listen_history ON listen_history.trackOwnerId = track.ownerId AND listen_history.trackId = track.id
        ORDER BY listen_history.listenedAt DESC
        """
    )
    abstract fun listened(): Flow<List<TrackEntity>>

    @Query("SELECT ownerId, id FROM track WHERE libraryPosition IS NOT NULL")
    abstract fun libraryKeys(): Flow<List<TrackKey>>

    @Query("SELECT ownerId, id FROM track WHERE cachedAt IS NOT NULL")
    abstract fun cachedKeys(): Flow<List<TrackKey>>

    @Query("SELECT ownerId, id FROM track WHERE cachedAt IS NOT NULL")
    abstract suspend fun currentCachedKeys(): List<TrackKey>

    @Transaction
    open suspend fun replaceLibrary(tracks: List<TrackEntity>) {
        clearLibraryPositions()
        tracks.forEachIndexed { position, track ->
            upsert(track.copy(libraryPosition = position, cachedAt = find(track.ownerId, track.id)?.cachedAt))
        }
        deleteOrphans()
    }

    @Transaction
    open suspend fun insertAtTop(track: TrackEntity) {
        shiftLibraryPositions()
        upsert((find(track.ownerId, track.id) ?: track).copy(libraryPosition = 0))
    }

    @Transaction
    open suspend fun removeFromLibrary(key: TrackKey) {
        val stored = find(key.ownerId, key.id) ?: return
        upsert(stored.copy(libraryPosition = null))
        deleteOrphans()
    }

    @Transaction
    open suspend fun markCached(track: TrackEntity, cachedAt: Long?) {
        upsert((find(track.ownerId, track.id) ?: track).copy(cachedAt = cachedAt))
        if (cachedAt == null) deleteOrphans()
    }

    @Transaction
    open suspend fun unmarkCached(keys: List<TrackKey>) {
        keys.forEach { clearCachedAt(it.ownerId, it.id) }
        deleteOrphans()
    }

    @Transaction
    open suspend fun clearCacheMarks() {
        clearAllCachedAt()
        deleteOrphans()
    }

    @Transaction
    open suspend fun recordListen(track: TrackEntity, listenedAt: Long) {
        if (find(track.ownerId, track.id) == null) upsert(track.copy(libraryPosition = null, cachedAt = null))
        upsertListen(ListenHistoryEntity(track.ownerId, track.id, listenedAt))
    }

    @Transaction
    open suspend fun clear(): Int {
        deleteListenHistory()
        return deleteTracks()
    }

    @Query("SELECT * FROM track WHERE ownerId = :ownerId AND id = :id")
    protected abstract suspend fun find(ownerId: Long, id: Long): TrackEntity?

    @Upsert
    protected abstract suspend fun upsert(track: TrackEntity)

    @Upsert
    protected abstract suspend fun upsertListen(entry: ListenHistoryEntity)

    @Query("UPDATE track SET libraryPosition = NULL")
    protected abstract suspend fun clearLibraryPositions()

    @Query("UPDATE track SET libraryPosition = libraryPosition + 1 WHERE libraryPosition IS NOT NULL")
    protected abstract suspend fun shiftLibraryPositions()

    @Query("UPDATE track SET cachedAt = NULL WHERE ownerId = :ownerId AND id = :id")
    protected abstract suspend fun clearCachedAt(ownerId: Long, id: Long)

    @Query("UPDATE track SET cachedAt = NULL")
    protected abstract suspend fun clearAllCachedAt()

    @Query(
        """
        DELETE FROM track
        WHERE libraryPosition IS NULL AND cachedAt IS NULL
          AND NOT EXISTS (SELECT 1 FROM listen_history WHERE trackOwnerId = track.ownerId AND trackId = track.id)
        """
    )
    protected abstract suspend fun deleteOrphans()

    @Query("DELETE FROM listen_history")
    protected abstract suspend fun deleteListenHistory()

    @Query("DELETE FROM track")
    protected abstract suspend fun deleteTracks(): Int
}
