package com.drunkbatya.vunder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, ListenHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
