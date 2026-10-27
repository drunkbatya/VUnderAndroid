package com.drunkbatya.vunder.feature.messages

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.messaging.MessageStore
import com.drunkbatya.vunder.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface MessagesRoute {
    data object Conversations : MessagesRoute
    data object Friends : MessagesRoute
    data class Chat(val peerId: Long, val title: String) : MessagesRoute
}

data class ChatRequest(val attachedTracks: List<Track> = emptyList(), val revealedTrack: Track? = null)

@HiltViewModel
class MessagesViewModel @Inject constructor(store: MessageStore) : ViewModel() {
    val backStack = mutableStateListOf<MessagesRoute>(MessagesRoute.Conversations)

    private val _chatRequests = MutableStateFlow<Map<Long, ChatRequest>>(emptyMap())
    val chatRequests: StateFlow<Map<Long, ChatRequest>> = _chatRequests.asStateFlow()

    val unreadTotal: StateFlow<Int> = store.unreadTotal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun openChat(peerId: Long, title: String, attachedTracks: List<Track> = emptyList()) {
        showChat(peerId, title)
        if (attachedTracks.isNotEmpty()) request(peerId) { it.copy(attachedTracks = it.attachedTracks + attachedTracks) }
    }

    fun revealTrack(peerId: Long, title: String, track: Track) {
        showChat(peerId, title)
        request(peerId) { it.copy(revealedTrack = track) }
    }

    fun consumeChatRequest(peerId: Long) = _chatRequests.update { it - peerId }

    internal fun showFriends() {
        if (backStack.last() != MessagesRoute.Friends) backStack += MessagesRoute.Friends
    }

    internal fun replaceFriendsWithChat(peerId: Long, title: String) {
        if (backStack.last() == MessagesRoute.Friends) backStack.removeAt(backStack.lastIndex)
        showChat(peerId, title)
    }

    internal fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun showChat(peerId: Long, title: String) {
        val existing = backStack.indexOfFirst { it is MessagesRoute.Chat && it.peerId == peerId }
        if (existing >= 0) {
            backStack.subList(existing + 1, backStack.size).clear()
        } else {
            backStack += MessagesRoute.Chat(peerId, title)
        }
    }

    private fun request(peerId: Long, change: (ChatRequest) -> ChatRequest) =
        _chatRequests.update { it + (peerId to change(it[peerId] ?: ChatRequest())) }
}
