package com.drunkbatya.vunder.core.messaging

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@Singleton
class MessagesSync @Inject constructor(
    private val api: MessagesApi,
    private val store: MessageStore,
    private val outbox: Outbox,
    private val longPoll: LongPollClient,
    private val network: NetworkMonitor,
    private val settings: SettingsRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private var sessionScope: CoroutineScope? = null
    private val markedReadUpTo = ConcurrentHashMap<Long, Long>()
    private val _refreshing = MutableStateFlow(false)
    private val _conversationTotal = MutableStateFlow(0)

    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val conversationTotal: StateFlow<Int> = _conversationTotal.asStateFlow()

    @Synchronized
    fun start() {
        if (sessionScope != null) return
        val scope = CoroutineScope(applicationScope.coroutineContext + SupervisorJob(applicationScope.coroutineContext.job))
        sessionScope = scope
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentStateFlow
        val active = combine(foreground, network.connectivity) { state, connectivity ->
            state.isAtLeast(Lifecycle.State.STARTED) && connectivity.isConnected
        }.distinctUntilChanged()
        scope.launch {
            active.filter { it }.collect { refreshConversations() }
        }
        scope.launch {
            combine(active, settings.settings) { isActive, userSettings -> isActive && userSettings[Toggle.LongPoll] }
                .distinctUntilChanged()
                .collectLatest { if (it) poll() }
        }
        scope.launch {
            outbox.sentToUnknownConversation.collect { refreshConversations() }
        }
        outbox.flush()
    }

    @Synchronized
    fun stop() {
        sessionScope?.cancel()
        sessionScope = null
    }

    suspend fun clear() {
        stop()
        markedReadUpTo.clear()
        _conversationTotal.value = 0
        store.clear()
    }

    fun refreshConversations() {
        val scope = synchronized(this) { sessionScope } ?: return
        if (!network.isConnected || !_refreshing.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                val page = api.conversations(offset = 0)
                store.replaceConversations(page.conversations, page.profiles)
                _conversationTotal.value = page.total
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "conversations refresh failed: ${e.message}")
            }
        }.invokeOnCompletion { _refreshing.value = false }
    }

    suspend fun loadConversations(offset: Int): ConversationPage? = remote("conversations page offset=$offset") {
        api.conversations(offset).also { store.saveProfiles(it.profiles) }
    }

    suspend fun refreshHistory(peerId: Long): Boolean? = remote("history peer=$peerId refresh") {
        val page = api.history(peerId)
        store.replaceLatestMessages(peerId, page.messages, page.profiles)
        store.updateConversation(peerId) { it.copy(inRead = page.inRead, outRead = page.outRead) }
        store.messageCount(peerId) < page.total
    }

    suspend fun loadOlderMessages(peerId: Long, beforeMessageId: Long): Boolean? = remote("history peer=$peerId before=$beforeMessageId") {
        val page = api.history(peerId, beforeMessageId)
        store.insertMessages(page.messages, page.profiles)
        page.messages.isNotEmpty() && store.messageCount(peerId) < page.total
    }

    suspend fun markRead(peerId: Long) {
        if (!settings.current()[Toggle.MarkAsRead] || !network.isConnected) return
        val conversation = store.findConversation(peerId) ?: return
        val latestIncoming = store.newestIncomingMessageId(peerId) ?: return
        if (latestIncoming <= conversation.inRead) return
        var claimed = false
        markedReadUpTo.compute(peerId) { _, marked ->
            if ((marked ?: 0) < latestIncoming) latestIncoming.also { claimed = true } else marked
        }
        if (!claimed) return
        val marked = remote("mark read peer=$peerId") {
            api.markAsRead(peerId, latestIncoming)
            store.updateConversation(peerId) { it.copy(inRead = latestIncoming, unreadCount = 0) }
        }
        if (marked == null) markedReadUpTo.remove(peerId, latestIncoming)
    }

    private suspend fun <T> remote(operation: String, block: suspend () -> T): T? {
        if (!network.isConnected) return null
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$operation failed: ${e.message}")
            null
        }
    }

    private suspend fun poll() {
        Log.i(TAG, "long poll start")
        try {
            var server: LongPollServer? = null
            var ts = 0L
            while (true) {
                try {
                    val current = server ?: api.longPollServer().also {
                        server = it
                        ts = it.ts
                    }
                    when (val response = longPoll.check(current, ts)) {
                        is LongPollResponse.Events -> {
                            ts = response.ts
                            handle(response.events)
                        }
                        is LongPollResponse.OutdatedTs -> ts = response.ts
                        LongPollResponse.KeyExpired -> server = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "long poll failed: ${e.message}")
                    server = null
                    delay(RETRY_DELAY)
                }
            }
        } finally {
            Log.i(TAG, "long poll stop")
        }
    }

    private suspend fun handle(events: List<LongPollEvent>) {
        val newIds = mutableListOf<Long>()
        for (event in events) {
            when (event) {
                is LongPollEvent.NewMessage -> newIds += event.id
                is LongPollEvent.FlagsSet -> if (event.mask and LongPollClient.DELETED_FLAG != 0L) {
                    Log.i(TAG, "message deleted id=${event.id} peer=${event.peerId}")
                    store.deleteMessage(event.id)
                    if (store.findConversation(event.peerId)?.lastMessage?.id == event.id) refreshConversations()
                }
                is LongPollEvent.IncomingRead -> store.updateConversation(event.peerId) { conversation ->
                    val unreadCount = if ((conversation.lastMessage?.id ?: 0) <= event.messageId) 0 else conversation.unreadCount
                    conversation.copy(inRead = event.messageId, unreadCount = unreadCount)
                }
                is LongPollEvent.OutgoingRead -> store.updateConversation(event.peerId) { it.copy(outRead = event.messageId) }
            }
        }
        if (newIds.isEmpty()) return
        try {
            val (messages, profiles) = api.messages(newIds)
            store.insertMessages(messages, profiles)
            val unknownPeer = messages.map { message ->
                store.updateConversation(message.peerId) { conversation ->
                    val isNewest = message.id > (conversation.lastMessage?.id ?: 0)
                    val isUnread = !message.isOutgoing && message.id > conversation.inRead
                    conversation.copy(
                        lastMessage = if (isNewest) message else conversation.lastMessage,
                        sortDate = if (isNewest) message.date else conversation.sortDate,
                        unreadCount = conversation.unreadCount + if (isUnread) 1 else 0,
                    )
                }
            }.any { known -> !known }
            if (unknownPeer) refreshConversations()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "new messages fetch failed ids=${newIds.size}: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "MessagesSync"
        val RETRY_DELAY = 5.seconds
    }
}
