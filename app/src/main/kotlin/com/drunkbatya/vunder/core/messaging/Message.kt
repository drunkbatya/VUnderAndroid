package com.drunkbatya.vunder.core.messaging

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.bool
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.objects
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@Entity(tableName = "message", indices = [Index("peerId")])
@TypeConverters(MessagingConverters::class)
data class Message(
    @PrimaryKey val id: Long,
    val peerId: Long,
    val fromId: Long,
    val date: Long,
    val text: String,
    val isOutgoing: Boolean,
    val attachments: List<MessageAttachment>,
    val randomId: Long,
) {
    val previewText: String
        get() = text.ifEmpty { attachments.firstOrNull()?.previewText.orEmpty() }

    val tracks: List<Track>
        get() = attachments.filterIsInstance<MessageAttachment.Audio>().map { it.track }

    companion object {
        fun from(json: JsonObject): Message? {
            val id = json.long("id") ?: return null
            val peerId = json.long("peer_id") ?: return null
            val fromId = json.long("from_id") ?: return null
            return Message(
                id = id,
                peerId = peerId,
                fromId = fromId,
                date = json.long("date") ?: 0,
                text = json.string("text").orEmpty(),
                isOutgoing = json.bool("out"),
                attachments = json.objects("attachments").mapNotNull(MessageAttachment::from),
                randomId = json.long("random_id") ?: 0,
            )
        }
    }
}
