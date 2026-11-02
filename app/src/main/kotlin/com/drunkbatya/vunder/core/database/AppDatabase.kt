package com.drunkbatya.vunder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.drunkbatya.vunder.core.messaging.Conversation
import com.drunkbatya.vunder.core.messaging.Message
import com.drunkbatya.vunder.core.messaging.MessagingDao
import com.drunkbatya.vunder.core.messaging.OutgoingMessage
import com.drunkbatya.vunder.core.messaging.Profile

@Database(
    entities = [
        TrackEntity::class,
        ListenHistoryEntity::class,
        Conversation::class,
        Message::class,
        Profile::class,
        OutgoingMessage::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun messagingDao(): MessagingDao
}

object TrackLargeCoverMigration : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE track ADD COLUMN largeCoverUrl TEXT")
    }
}
