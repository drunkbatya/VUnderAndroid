package com.drunkbatya.vunder.core.messaging

import android.util.Log
import androidx.annotation.MainThread
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.player.PlayerController
import com.drunkbatya.vunder.core.player.PlayerState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@MainThread
@Singleton
class ConversationQueueLoader @Inject constructor(
    private val api: MessagesApi,
    private val player: PlayerController,
    private val network: NetworkMonitor,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private val scope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate)
    private var source: QueueSource.Conversation? = null
    private var nextFrom: String? = null
    private var job: Job? = null

    init {
        scope.launch {
            player.state.collect(::loadNextPageIfNeeded)
        }
    }

    fun start(source: QueueSource.Conversation) {
        job?.cancel()
        this.source = source
        nextFrom = null
        job = scope.launch {
            try {
                val page = api.audioAttachments(source.peerId)
                player.replaceQueue(page.tracks, source)
                nextFrom = page.nextFrom
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "conversation queue peer=${source.peerId} first page failed: ${e.message}")
            }
            job = null
            loadNextPageIfNeeded(player.state.value)
        }
    }

    private fun loadNextPageIfNeeded(state: PlayerState) {
        val source = source ?: return
        if (state.source != source) {
            this.source = null
            job?.cancel()
            job = null
            return
        }
        val from = nextFrom ?: return
        if (job != null || !network.isConnected || state.remainingCount > PREFETCH_THRESHOLD) return
        job = scope.launch {
            try {
                val page = api.audioAttachments(source.peerId, startFrom = from)
                player.appendToQueue(page.tracks, source)
                nextFrom = page.nextFrom.takeIf { page.tracks.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nextFrom = null
                Log.e(TAG, "conversation queue peer=${source.peerId} from=$from failed: ${e.message}")
            }
            job = null
        }
    }

    private companion object {
        const val TAG = "ConversationQueue"
        const val PREFETCH_THRESHOLD = 5
    }
}
