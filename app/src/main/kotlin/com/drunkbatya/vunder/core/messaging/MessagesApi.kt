package com.drunkbatya.vunder.core.messaging

import android.util.Log
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.VkApiClient
import com.drunkbatya.vunder.core.network.VkApiException
import com.drunkbatya.vunder.core.network.VkRequest
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.objects
import com.drunkbatya.vunder.core.network.string
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

data class ConversationPage(val conversations: List<Conversation>, val profiles: List<Profile>, val total: Int)

data class HistoryPage(
    val messages: List<Message>,
    val profiles: List<Profile>,
    val total: Int,
    val inRead: Long,
    val outRead: Long,
)

data class FriendPage(val friends: List<Profile>, val total: Int)

data class AudioAttachmentPage(val tracks: List<Track>, val nextFrom: String?)

data class LongPollServer(val url: String, val key: String, val ts: Long)

@Singleton
class MessagesApi @Inject constructor(private val api: VkApiClient) {
    suspend fun conversations(offset: Int, count: Int = CONVERSATIONS_PAGE_SIZE): ConversationPage {
        val response = api.responseObject(
            VkRequest(
                method = "messages.getConversations",
                version = CONVERSATIONS_VERSION,
                parameters = listOf(
                    "offset" to offset.toString(),
                    "count" to count.toString(),
                    "extended" to "1",
                    "filter" to "all",
                    "fields" to PROFILE_FIELDS,
                ),
            ),
        )
        val profiles = Profile.parseAll(response)
        val profilesById = profiles.associateBy { it.id }
        val conversations = response.objects("items").mapNotNull { Conversation.from(it, profilesById) }
        val total = response.int("count") ?: 0
        Log.i(TAG, "conversations offset=$offset items=${conversations.size} total=$total")
        return ConversationPage(conversations, profiles, total)
    }

    suspend fun history(peerId: Long, beforeMessageId: Long? = null, count: Int = HISTORY_PAGE_SIZE): HistoryPage {
        val parameters = buildList {
            add("peer_id" to peerId.toString())
            add("offset" to "0")
            add("count" to (count + if (beforeMessageId == null) 0 else 1).toString())
            add("extended" to "1")
            add("fields" to PROFILE_FIELDS)
            beforeMessageId?.let { add("start_message_id" to it.toString()) }
        }
        val response = api.responseObject(VkRequest("messages.getHistory", parameters, HISTORY_VERSION))
        val messages = response.objects("items")
            .mapNotNull(Message::from)
            .filter { beforeMessageId == null || it.id < beforeMessageId }
            .sortedBy { it.id }
        val conversation = response.objects("conversations").firstOrNull()
        val total = response.int("count") ?: 0
        Log.i(TAG, "history peer=$peerId before=${beforeMessageId ?: 0} items=${messages.size} total=$total")
        return HistoryPage(
            messages = messages,
            profiles = Profile.parseAll(response),
            total = total,
            inRead = conversation?.long("in_read") ?: 0,
            outRead = conversation?.long("out_read") ?: 0,
        )
    }

    suspend fun messages(ids: List<Long>): Pair<List<Message>, List<Profile>> {
        val response = api.responseObject(
            VkRequest(
                method = "messages.getById",
                version = HISTORY_VERSION,
                parameters = listOf(
                    "message_ids" to ids.joinToString(","),
                    "extended" to "1",
                    "fields" to PROFILE_FIELDS,
                ),
            ),
        )
        val messages = response.objects("items").mapNotNull(Message::from)
        Log.i(TAG, "messages by id requested=${ids.size} items=${messages.size}")
        return messages to Profile.parseAll(response)
    }

    suspend fun send(message: OutgoingMessage): Long {
        val parameters = buildList {
            add("peer_id" to message.peerId.toString())
            add("random_id" to message.randomId.toString())
            add("dont_parse_links" to "1")
            if (message.text.isNotEmpty()) add("message" to message.text)
            if (message.attachments.isNotEmpty()) add("attachment" to message.attachments.joinToString(",") { "audio${it.fullId}" })
        }
        val version = if (Peer.isGroup(message.peerId)) "5.81" else "5.84"
        val json = api.call(VkRequest("messages.send", parameters, version))
        val id = (json["response"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            ?: throw VkApiException.MalformedResponse("messages.send")
        Log.i(TAG, "sent peer=${message.peerId} id=$id attachments=${message.attachments.size}")
        return id
    }

    suspend fun markAsRead(peerId: Long, upToMessageId: Long) {
        api.call(
            VkRequest(
                method = "messages.markAsRead",
                version = HISTORY_VERSION,
                parameters = listOf("peer_id" to peerId.toString(), "start_message_id" to upToMessageId.toString()),
            ),
        )
        Log.i(TAG, "marked read peer=$peerId up to $upToMessageId")
    }

    suspend fun setOffline() {
        api.call(VkRequest("account.setOffline"))
        Log.i(TAG, "presence set offline")
    }

    suspend fun longPollServer(): LongPollServer {
        val response = api.responseObject(
            VkRequest(
                method = "messages.getLongPollServer",
                version = HISTORY_VERSION,
                parameters = listOf("lp_version" to "3", "need_pts" to "0"),
            ),
        )
        val server = response.string("server")
        val key = response.string("key")
        val ts = response.long("ts")
        if (server == null || key == null || ts == null) throw VkApiException.MalformedResponse("messages.getLongPollServer")
        val url = if (server.startsWith("http")) server else "https://$server"
        Log.i(TAG, "long poll server $url ts=$ts")
        return LongPollServer(url, key, ts)
    }

    suspend fun audioAttachments(peerId: Long, startFrom: String? = null, count: Int = AUDIO_ATTACHMENTS_PAGE_SIZE): AudioAttachmentPage {
        val parameters = buildList {
            add("peer_id" to peerId.toString())
            add("media_type" to "audio")
            add("count" to count.toString())
            startFrom?.let { add("start_from" to it) }
        }
        val response = api.responseObject(VkRequest("messages.getHistoryAttachments", parameters, HISTORY_VERSION))
        val tracks = response.objects("items").mapNotNull { item ->
            (item.obj("attachment")?.let(MessageAttachment::from) as? MessageAttachment.Audio)?.track
        }
        val nextFrom = response.string("next_from")?.takeUnless { it.isEmpty() }
        Log.i(TAG, "audio attachments peer=$peerId from=${startFrom ?: "-"} items=${tracks.size} next=${nextFrom ?: "-"}")
        return AudioAttachmentPage(tracks, nextFrom)
    }

    suspend fun friends(offset: Int, count: Int = FRIENDS_PAGE_SIZE): FriendPage {
        val page = friendPage(
            "friends.get",
            listOf("order" to "hints", "fields" to PROFILE_FIELDS, "offset" to offset.toString(), "count" to count.toString()),
        )
        Log.i(TAG, "friends offset=$offset items=${page.friends.size} total=${page.total}")
        return page
    }

    suspend fun searchFriends(query: String, offset: Int, count: Int = FRIENDS_PAGE_SIZE): FriendPage {
        val page = friendPage(
            "friends.search",
            listOf("q" to query, "fields" to PROFILE_FIELDS, "offset" to offset.toString(), "count" to count.toString()),
        )
        Log.i(TAG, "friends search '$query' offset=$offset items=${page.friends.size}")
        return page
    }

    private suspend fun friendPage(method: String, parameters: List<Pair<String, String>>): FriendPage {
        val response = api.responseObject(VkRequest(method, parameters))
        return FriendPage(response.objects("items").mapNotNull(Profile::fromUser), response.int("count") ?: 0)
    }

    companion object {
        const val CONVERSATIONS_PAGE_SIZE = 30
        const val HISTORY_PAGE_SIZE = 30
        const val FRIENDS_PAGE_SIZE = 50
        const val AUDIO_ATTACHMENTS_PAGE_SIZE = 200

        private const val TAG = "MessagesApi"
        private const val CONVERSATIONS_VERSION = "5.163"
        private const val HISTORY_VERSION = "5.129"
        private const val PROFILE_FIELDS = "first_name,last_name,photo_100,photo_50"
    }
}
