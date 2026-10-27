package com.drunkbatya.vunder.core.messaging

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.drunkbatya.vunder.core.model.Track

@Entity(tableName = "outgoing_message")
@TypeConverters(MessagingConverters::class)
data class OutgoingMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerId: Long,
    val text: String,
    val attachments: List<Track>,
    val randomId: Long = (1..Int.MAX_VALUE).random().toLong(),
    val failure: String? = null,
) {
    val previewText: String
        get() {
            if (text.isNotEmpty()) return text
            val first = attachments.firstOrNull() ?: return ""
            return if (attachments.size > 1) "Audio: ${attachments.size} tracks" else "Audio: ${first.artist} - ${first.title}"
        }
}
