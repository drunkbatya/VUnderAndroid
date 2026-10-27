package com.drunkbatya.vunder.core.messaging

import android.util.Log
import androidx.room.withTransaction
import com.drunkbatya.vunder.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MessageStore @Inject constructor(private val database: AppDatabase) {
    private val dao = database.messagingDao()

    val conversations: Flow<List<Conversation>> = dao.conversations()

    val unreadTotal: Flow<Int> = dao.unreadTotal()

    val outgoing: Flow<List<OutgoingMessage>> = dao.outgoing()

    fun conversation(peerId: Long): Flow<Conversation?> = dao.conversation(peerId)

    fun messages(peerId: Long): Flow<List<Message>> = dao.messages(peerId)

    fun profiles(peerId: Long): Flow<Map<Long, Profile>> = dao.chatProfiles(peerId).map { profiles -> profiles.associateBy { it.id } }

    fun outgoing(peerId: Long): Flow<List<OutgoingMessage>> = dao.outgoing(peerId)

    suspend fun findConversation(peerId: Long): Conversation? = dao.findConversation(peerId)

    suspend fun replaceConversations(conversations: List<Conversation>, profiles: List<Profile>) {
        database.withTransaction {
            dao.deleteConversations()
            dao.upsertConversations(conversations)
            dao.upsertProfiles(profiles)
        }
        Log.i(TAG, "conversations replaced count=${conversations.size}")
    }

    suspend fun updateConversation(peerId: Long, change: (Conversation) -> Conversation): Boolean = database.withTransaction {
        val conversation = dao.findConversation(peerId) ?: return@withTransaction false
        dao.upsertConversations(listOf(change(conversation)))
        true
    }

    suspend fun messageCount(peerId: Long): Int = dao.messageCount(peerId)

    suspend fun newestIncomingMessageId(peerId: Long): Long? = dao.newestIncomingMessageId(peerId)

    suspend fun replaceLatestMessages(peerId: Long, messages: List<Message>, profiles: List<Profile>) {
        database.withTransaction {
            val oldestFetched = messages.firstOrNull()?.id
            val newestCached = dao.newestMessageId(peerId)
            if (oldestFetched == null || newestCached == null || newestCached < oldestFetched) {
                dao.deleteMessages(peerId)
            } else {
                dao.deleteMessagesNotIn(peerId, oldestFetched, messages.map { it.id })
            }
            dao.upsertMessages(messages)
            dao.upsertProfiles(profiles)
        }
        Log.i(TAG, "messages cached peer=$peerId count=${messages.size}")
    }

    suspend fun insertMessages(messages: List<Message>, profiles: List<Profile>) {
        database.withTransaction {
            dao.upsertMessages(messages)
            dao.upsertProfiles(profiles)
        }
    }

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun saveProfiles(profiles: List<Profile>) = dao.upsertProfiles(profiles)

    suspend fun firstPendingOutgoing(): OutgoingMessage? = dao.firstPendingOutgoing()

    suspend fun insertOutgoing(message: OutgoingMessage) = dao.insertOutgoing(message)

    suspend fun setOutgoingFailure(id: Long, failure: String?) = dao.setOutgoingFailure(id, failure)

    suspend fun deleteOutgoing(id: Long) = dao.deleteOutgoing(id)

    suspend fun completeOutgoing(outgoing: OutgoingMessage, sent: Message): Boolean = database.withTransaction {
        dao.deleteOutgoing(outgoing.id)
        dao.upsertMessages(listOf(sent))
        updateConversation(sent.peerId) { it.copy(lastMessage = sent, sortDate = sent.date) }
    }

    suspend fun clear() {
        database.withTransaction {
            dao.deleteAllOutgoing()
            dao.deleteAllMessages()
            dao.deleteConversations()
            dao.deleteProfiles()
        }
        Log.i(TAG, "message store cleared")
    }

    private companion object {
        const val TAG = "MessageStore"
    }
}
