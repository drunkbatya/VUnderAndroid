package com.drunkbatya.vunder.core.messaging

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.drunkbatya.vunder.core.network.bool
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.json.JsonObject

@Entity(tableName = "conversation", indices = [Index("sortDate")])
@TypeConverters(MessagingConverters::class)
data class Conversation(
    @PrimaryKey val peerId: Long,
    val title: String,
    val photoUrl: String?,
    val unreadCount: Int,
    val inRead: Long,
    val outRead: Long,
    val canWrite: Boolean,
    val lastMessage: Message?,
    val sortDate: Long,
) {
    companion object {
        fun from(json: JsonObject, profiles: Map<Long, Profile>): Conversation? {
            val conversation = json.obj("conversation") ?: return null
            val peerId = conversation.obj("peer")?.long("id") ?: return null
            val chatSettings = conversation.obj("chat_settings")
            val lastMessage = json.obj("last_message")?.let(Message::from)
            return Conversation(
                peerId = peerId,
                title = if (chatSettings != null) chatSettings.string("title") ?: "Chat" else profiles[peerId]?.name ?: "id$peerId",
                photoUrl = if (chatSettings != null) chatSettings.obj("photo")?.string("photo_100") else profiles[peerId]?.photoUrl,
                unreadCount = conversation.int("unread_count") ?: 0,
                inRead = conversation.long("in_read") ?: 0,
                outRead = conversation.long("out_read") ?: 0,
                canWrite = conversation.obj("can_write")?.bool("allowed") ?: true,
                lastMessage = lastMessage,
                sortDate = lastMessage?.date ?: 0,
            )
        }
    }
}
