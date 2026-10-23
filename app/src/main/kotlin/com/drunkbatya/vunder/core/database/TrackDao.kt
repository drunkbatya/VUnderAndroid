package com.drunkbatya.vunder.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM track WHERE libraryPosition IS NOT NULL ORDER BY libraryPosition")
    fun library(): Flow<List<TrackEntity>>

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)
}
