package com.drunkbatya.vunder.core.messaging

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MessagingDao {
    @Query("SELECT * FROM conversation ORDER BY sortDate DESC")
    fun conversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversation WHERE peerId = :peerId")
    fun conversation(peerId: Long): Flow<Conversation?>

    @Query("SELECT * FROM conversation WHERE peerId = :peerId")
    suspend fun findConversation(peerId: Long): Conversation?

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversation")
    fun unreadTotal(): Flow<Int>

    @Upsert
    suspend fun upsertConversations(conversations: List<Conversation>)

    @Query("DELETE FROM conversation")
    suspend fun deleteConversations()

    @Query("SELECT * FROM message WHERE peerId = :peerId ORDER BY id")
    fun messages(peerId: Long): Flow<List<Message>>

    @Query("SELECT COUNT(*) FROM message WHERE peerId = :peerId")
    suspend fun messageCount(peerId: Long): Int

    @Query("SELECT MAX(id) FROM message WHERE peerId = :peerId")
    suspend fun newestMessageId(peerId: Long): Long?

    @Query("SELECT MAX(id) FROM message WHERE peerId = :peerId AND isOutgoing = 0")
    suspend fun newestIncomingMessageId(peerId: Long): Long?

    @Upsert
    suspend fun upsertMessages(messages: List<Message>)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM message WHERE peerId = :peerId")
    suspend fun deleteMessages(peerId: Long)

    @Query("DELETE FROM message WHERE peerId = :peerId AND id >= :fromId AND id NOT IN (:keptIds)")
    suspend fun deleteMessagesNotIn(peerId: Long, fromId: Long, keptIds: List<Long>)

    @Query("DELETE FROM message")
    suspend fun deleteAllMessages()

    @Query("SELECT * FROM profile WHERE id IN (SELECT fromId FROM message WHERE peerId = :peerId)")
    fun chatProfiles(peerId: Long): Flow<List<Profile>>

    @Upsert
    suspend fun upsertProfiles(profiles: List<Profile>)

    @Query("DELETE FROM profile")
    suspend fun deleteProfiles()

    @Query("SELECT * FROM outgoing_message ORDER BY id")
    fun outgoing(): Flow<List<OutgoingMessage>>

    @Query("SELECT * FROM outgoing_message WHERE peerId = :peerId ORDER BY id")
    fun outgoing(peerId: Long): Flow<List<OutgoingMessage>>

    @Query("SELECT * FROM outgoing_message WHERE failure IS NULL ORDER BY id LIMIT 1")
    suspend fun firstPendingOutgoing(): OutgoingMessage?

    @Insert
    suspend fun insertOutgoing(message: OutgoingMessage)

    @Query("UPDATE outgoing_message SET failure = :failure WHERE id = :id")
    suspend fun setOutgoingFailure(id: Long, failure: String?)

    @Query("DELETE FROM outgoing_message WHERE id = :id")
    suspend fun deleteOutgoing(id: Long)

    @Query("DELETE FROM outgoing_message")
    suspend fun deleteAllOutgoing()
}
