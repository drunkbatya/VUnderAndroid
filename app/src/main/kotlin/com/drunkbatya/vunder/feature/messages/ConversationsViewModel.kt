package com.drunkbatya.vunder.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.messaging.Conversation
import com.drunkbatya.vunder.core.messaging.MessageStore
import com.drunkbatya.vunder.core.messaging.MessagesSync
import com.drunkbatya.vunder.core.messaging.OutgoingMessage
import com.drunkbatya.vunder.core.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val outgoingByPeer: Map<Long, OutgoingMessage> = emptyMap(),
    val refreshing: Boolean = false,
    val isConnected: Boolean = true,
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    store: MessageStore,
    private val sync: MessagesSync,
    network: NetworkMonitor,
) : ViewModel() {
    private val pagedConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private var loadMoreJob: Job? = null

    val state: StateFlow<ConversationsUiState> = combine(
        store.conversations,
        pagedConversations,
        store.outgoing,
        sync.refreshing,
        network.connectivity.map { it.isConnected },
    ) { cached, paged, outgoing, refreshing, isConnected ->
        val cachedIds = cached.mapTo(HashSet()) { it.peerId }
        ConversationsUiState(
            conversations = cached + paged.filter { it.peerId !in cachedIds },
            outgoingByPeer = outgoing.associateBy { it.peerId },
            refreshing = refreshing,
            isConnected = isConnected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationsUiState())

    init {
        viewModelScope.launch {
            sync.refreshing.drop(1).filter { !it }.collect { pagedConversations.value = emptyList() }
        }
    }

    fun refresh() = sync.refreshConversations()

    fun loadMore() {
        val shown = state.value.conversations.size
        if (loadMoreJob?.isActive == true || shown >= sync.conversationTotal.value) return
        loadMoreJob = viewModelScope.launch {
            val page = sync.loadConversations(offset = shown) ?: return@launch
            pagedConversations.update { current -> current + page.conversations.filter { new -> current.none { it.peerId == new.peerId } } }
        }
    }
}
