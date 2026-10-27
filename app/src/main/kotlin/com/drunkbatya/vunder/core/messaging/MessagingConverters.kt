package com.drunkbatya.vunder.core.messaging

import androidx.room.TypeConverter
import com.drunkbatya.vunder.core.model.Track
import kotlinx.serialization.json.Json

class MessagingConverters {
    @TypeConverter
    fun attachmentsToJson(attachments: List<MessageAttachment>): String = json.encodeToString(attachments)

    @TypeConverter
    fun attachmentsFromJson(encoded: String): List<MessageAttachment> = json.decodeFromString(encoded)

    @TypeConverter
    fun tracksToJson(tracks: List<Track>): String = json.encodeToString(tracks)

    @TypeConverter
    fun tracksFromJson(encoded: String): List<Track> = json.decodeFromString(encoded)

    @TypeConverter
    fun messageToJson(message: Message?): String? = message?.let(json::encodeToString)

    @TypeConverter
    fun messageFromJson(encoded: String?): Message? = encoded?.let { json.decodeFromString<Message>(it) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
