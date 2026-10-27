package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.Conversation
import com.drunkbatya.vunder.core.messaging.ConversationQueueLoader
import com.drunkbatya.vunder.core.messaging.Message
import com.drunkbatya.vunder.core.messaging.MessageStore
import com.drunkbatya.vunder.core.messaging.MessagesSync
import com.drunkbatya.vunder.core.messaging.Outbox
import com.drunkbatya.vunder.core.messaging.OutgoingMessage
import com.drunkbatya.vunder.core.messaging.Peer
import com.drunkbatya.vunder.core.messaging.Profile
import com.drunkbatya.vunder.core.messaging.VoiceMessage
import com.drunkbatya.vunder.core.messaging.VoicePlayer
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.core.player.PlayerController
import com.drunkbatya.vunder.ui.track.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatItem {
    val key: Any

    data class DayHeader(val date: LocalDate) : ChatItem {
        override val key get() = "day-$date"
    }

    data class MessageRow(val message: Message, val senderName: String?, val isRead: Boolean) : ChatItem {
        override val key get() = message.id
    }

    data class OutgoingRow(val outgoing: OutgoingMessage) : ChatItem {
        override val key get() = "outgoing-${outgoing.id}"
    }
}

data class ChatPlayback(
    val currentTrack: Track? = null,
    val isMusicPlaying: Boolean = false,
    val playingVoice: VoiceMessage? = null,
)

data class ChatUiState(
    val title: String,
    val items: List<ChatItem> = emptyList(),
    val attachments: List<Track> = emptyList(),
    val loading: Boolean = false,
    val isConnected: Boolean = true,
    val playback: ChatPlayback = ChatPlayback(),
)

sealed interface ChatEvent {
    data class ScrollTo(val index: Int) : ChatEvent
    data class Notice(val text: UiText) : ChatEvent
}

sealed interface ChatAction {
    data object Send : ChatAction
    data object LoadOlder : ChatAction
    data class Attach(val tracks: List<Track>) : ChatAction
    data class RemoveAttachment(val index: Int) : ChatAction
    data class PlayTrack(val track: Track) : ChatAction
    data class ToggleVoice(val voice: VoiceMessage) : ChatAction
    data class Retry(val outgoing: OutgoingMessage) : ChatAction
    data class Delete(val outgoing: OutgoingMessage) : ChatAction
}

@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted private val route: MessagesRoute.Chat,
    store: MessageStore,
    private val sync: MessagesSync,
    private val outbox: Outbox,
    private val network: NetworkMonitor,
    private val player: PlayerController,
    private val voicePlayer: VoicePlayer,
    private val queueLoader: ConversationQueueLoader,
) : ViewModel() {
    private val peerId = route.peerId
    private val loading = MutableStateFlow(false)
    private val attachments = MutableStateFlow<List<Track>>(emptyList())
    private val visible = MutableStateFlow(false)
    private val eventChannel = Channel<ChatEvent>(Channel.BUFFERED)
    private var hasOlder = false
    private var refreshJob: Job? = null
    private var olderJob: Job? = null

    val events: Flow<ChatEvent> = eventChannel.receiveAsFlow()

    val draft = TextFieldState()

    private val items: Flow<List<ChatItem>> = combine(
        store.messages(peerId),
        store.outgoing(peerId),
        store.profiles(peerId),
        store.conversation(peerId),
        ::chatItems,
    )

    private val playback: Flow<ChatPlayback> = combine(player.state, voicePlayer.state) { music, voice ->
        ChatPlayback(music.current, music.isPlaying, voice.current?.takeIf { voice.isPlaying })
    }

    val state: StateFlow<ChatUiState> = combine(
        items,
        attachments,
        combine(loading, network.connectivity) { loading, connectivity -> loading to connectivity.isConnected },
        playback,
    ) { items, attachments, (loading, isConnected), playback ->
        ChatUiState(route.title, items, attachments, loading, isConnected, playback)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(route.title))

    init {
        refreshLatest()
        viewModelScope.launch {
            network.connectivity.map { it.isConnected }.distinctUntilChanged().drop(1).filter { it }.collect { refreshLatest() }
        }
        viewModelScope.launch {
            combine(visible, store.messages(peerId)) { isVisible, _ -> isVisible }.filter { it }.collect { sync.markRead(peerId) }
        }
    }

    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
    }

    fun handle(request: ChatRequest) {
        if (request.attachedTracks.isNotEmpty()) attach(request.attachedTracks)
        request.revealedTrack?.let(::reveal)
    }

    fun onAction(action: ChatAction) {
        when (action) {
            ChatAction.Send -> send()
            ChatAction.LoadOlder -> loadOlder()
            is ChatAction.Attach -> attach(action.tracks)
            is ChatAction.RemoveAttachment -> attachments.update { it.filterIndexed { index, _ -> index != action.index } }
            is ChatAction.PlayTrack -> playTrack(action.track)
            is ChatAction.ToggleVoice -> voicePlayer.toggle(action.voice)
            is ChatAction.Retry -> outbox.retry(action.outgoing)
            is ChatAction.Delete -> outbox.delete(action.outgoing)
        }
    }

    override fun onCleared() {
        voicePlayer.stop()
    }

    private fun send() {
        val text = draft.text.trim().toString()
        val tracks = attachments.value
        if (text.isEmpty() && tracks.isEmpty()) return
        draft.clearText()
        attachments.value = emptyList()
        outbox.enqueue(peerId, text, tracks)
    }

    private fun attach(tracks: List<Track>) = attachments.update { current ->
        (current + tracks.filter { track -> current.none { it.isSame(track) } }).take(MAX_ATTACHMENTS)
    }

    private fun playTrack(track: Track) {
        if (track.isSame(player.state.value.current)) {
            player.togglePlayPause()
            return
        }
        val source = QueueSource.Conversation(peerId, route.title)
        val tracks = state.value.items.filterIsInstance<ChatItem.MessageRow>().flatMap { it.message.tracks.asReversed() }
        player.play(track, tracks.takeIf { track in it } ?: listOf(track), source)
        queueLoader.start(source)
    }

    private fun reveal(track: Track) {
        viewModelScope.launch {
            val items = state.first { current -> current.items.any { it is ChatItem.MessageRow } }.items
            val index = items.indexOfFirst { it is ChatItem.MessageRow && it.message.tracks.any(track::isSame) }
            eventChannel.send(if (index >= 0) ChatEvent.ScrollTo(index) else ChatEvent.Notice(UiText.Resource(R.string.messages_track_not_in_chat)))
        }
    }

    private fun refreshLatest() {
        if (refreshJob?.isActive == true || !network.isConnected) return
        refreshJob = viewModelScope.launch {
            loading.value = true
            sync.refreshHistory(peerId)?.let { hasOlder = it }
            loading.value = false
        }
    }

    private fun loadOlder() {
        val oldest = state.value.items.lastOrNull { it is ChatItem.MessageRow } as? ChatItem.MessageRow ?: return
        if (!hasOlder || olderJob?.isActive == true || refreshJob?.isActive == true) return
        olderJob = viewModelScope.launch {
            sync.loadOlderMessages(peerId, oldest.message.id)?.let { hasOlder = it }
        }
    }

    private fun chatItems(
        messages: List<Message>,
        outgoing: List<OutgoingMessage>,
        profiles: Map<Long, Profile>,
        conversation: Conversation?,
    ): List<ChatItem> = buildList {
        val outRead = conversation?.outRead ?: 0
        outgoing.asReversed().forEach { add(ChatItem.OutgoingRow(it)) }
        val newestFirst = messages.asReversed()
        newestFirst.forEachIndexed { index, message ->
            val senderName = if (Peer.isChat(peerId) && !message.isOutgoing) profiles[message.fromId]?.name ?: "id${message.fromId}" else null
            add(ChatItem.MessageRow(message, senderName, isRead = message.isOutgoing && message.id <= outRead))
            val day = MessageDates.localDate(message.date)
            if (newestFirst.getOrNull(index + 1)?.let { MessageDates.localDate(it.date) } != day) add(ChatItem.DayHeader(day))
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(route: MessagesRoute.Chat): ChatViewModel
    }

    companion object {
        const val MAX_ATTACHMENTS = 10

        private const val TAG = "Chat"
    }
}
