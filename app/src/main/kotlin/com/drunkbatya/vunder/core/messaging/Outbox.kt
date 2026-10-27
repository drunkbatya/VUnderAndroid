package com.drunkbatya.vunder.core.messaging

import android.util.Log
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.network.VkApiException
import com.drunkbatya.vunder.core.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Singleton
class Outbox @Inject constructor(
    private val api: MessagesApi,
    private val store: MessageStore,
    private val network: NetworkMonitor,
    private val sessionStore: SessionStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)
    private val sentToUnknownPeer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var setOfflineJob: Job? = null

    val sentToUnknownConversation: SharedFlow<Unit> = sentToUnknownPeer.asSharedFlow()

    init {
        scope.launch {
            flushRequests.receiveAsFlow().collect { sendPending() }
        }
        scope.launch {
            network.connectivity.collect { flush() }
        }
    }

    fun enqueue(peerId: Long, text: String, attachments: List<Track>) {
        Log.i(TAG, "enqueue peer=$peerId text=${text.length} chars attachments=${attachments.joinToString(",") { it.fullId }}")
        scope.launch {
            store.insertOutgoing(OutgoingMessage(peerId = peerId, text = text, attachments = attachments))
            flush()
        }
    }

    fun retry(message: OutgoingMessage) {
        scope.launch {
            store.setOutgoingFailure(message.id, failure = null)
            flush()
        }
    }

    fun delete(message: OutgoingMessage) {
        scope.launch { store.deleteOutgoing(message.id) }
    }

    fun flush() {
        if (network.isConnected) flushRequests.trySend(Unit)
    }

    private suspend fun sendPending() {
        while (network.isConnected) {
            val message = store.firstPendingOutgoing() ?: return
            val userId = sessionStore.current()?.userId ?: return
            try {
                val id = api.send(message)
                val sent = Message(
                    id = id,
                    peerId = message.peerId,
                    fromId = userId,
                    date = System.currentTimeMillis() / 1000,
                    text = message.text,
                    isOutgoing = true,
                    attachments = message.attachments.map(MessageAttachment::Audio),
                    randomId = message.randomId,
                )
                val conversationKnown = store.completeOutgoing(message, sent)
                scheduleSetOffline()
                if (!conversationKnown) sentToUnknownPeer.tryEmit(Unit)
            } catch (e: VkApiException.Api) {
                Log.e(TAG, "send peer=${message.peerId} rejected ${e.error.code}: ${e.error.message}")
                store.setOutgoingFailure(message.id, "${e.error.message} (code ${e.error.code})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "send peer=${message.peerId} failed: ${e.message}")
                return
            }
        }
    }

    private fun scheduleSetOffline() {
        setOfflineJob?.cancel()
        setOfflineJob = scope.launch {
            delay(SET_OFFLINE_DELAY)
            try {
                api.setOffline()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "set offline failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "Outbox"
        val SET_OFFLINE_DELAY = 2.seconds
    }
}
