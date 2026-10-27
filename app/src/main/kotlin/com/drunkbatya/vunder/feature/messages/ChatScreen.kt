package com.drunkbatya.vunder.feature.messages

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.MessageAttachment
import com.drunkbatya.vunder.core.messaging.OutgoingMessage
import com.drunkbatya.vunder.core.messaging.VoiceMessage
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.ui.components.BackTopBar
import com.drunkbatya.vunder.ui.components.LoadMoreEffect
import com.drunkbatya.vunder.ui.components.MenuItem
import com.drunkbatya.vunder.ui.components.MenuSheet
import com.drunkbatya.vunder.ui.components.StatusText
import com.drunkbatya.vunder.ui.track.NoticeToasts
import com.drunkbatya.vunder.ui.track.TrackActionsViewModel
import com.drunkbatya.vunder.ui.track.TrackMenuSheet
import com.drunkbatya.vunder.ui.track.commonMenuItems
import com.drunkbatya.vunder.ui.track.resolve
import kotlinx.coroutines.flow.Flow

@Composable
internal fun ChatRoute(
    route: MessagesRoute.Chat,
    request: ChatRequest?,
    onRequestHandled: () -> Unit,
    onBack: () -> Unit,
    trackActions: TrackActionsViewModel = hiltViewModel(),
) {
    val viewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(creationCallback = { it.create(route) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeToasts(trackActions.notices)
    LaunchedEffect(request) {
        if (request != null) {
            viewModel.handle(request)
            onRequestHandled()
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.setVisible(true)
        onPauseOrDispose { viewModel.setVisible(false) }
    }
    ChatScreen(state, viewModel.draft, viewModel.events, viewModel::onAction, onBack, trackActions::cover, trackActions::commonMenuItems)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    state: ChatUiState,
    draft: TextFieldState,
    events: Flow<ChatEvent>,
    onAction: (ChatAction) -> Unit,
    onBack: () -> Unit,
    cover: (Track) -> Any?,
    trackMenu: (Track) -> List<MenuItem>,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var selectedOutgoing by remember { mutableStateOf<OutgoingMessage?>(null) }
    var pickingTracks by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ChatEvent.ScrollTo -> listState.animateScrollToItem(event.index)
                is ChatEvent.Notice -> Toast.makeText(context, event.text.resolve(context), Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(state.items.firstOrNull()?.key) {
        if (listState.firstVisibleItemIndex <= 1 && !listState.isScrollInProgress) listState.animateScrollToItem(0)
    }
    LoadMoreEffect(listState, threshold = 5, retryKey = state.loading) { onAction(ChatAction.LoadOlder) }

    Scaffold(
        topBar = {
            BackTopBar(state.title, onBack)
        },
        bottomBar = {
            ChatInputBar(
                draft = draft,
                attachments = state.attachments,
                onAttach = { pickingTracks = true },
                onRemoveAttachment = { onAction(ChatAction.RemoveAttachment(it)) },
                onSend = { onAction(ChatAction.Send) },
                modifier = Modifier.imePadding(),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                    ChatItemContent(
                        item = item,
                        state = state,
                        onAction = onAction,
                        onTrackMenu = { menuTrack = it },
                        onOutgoingClick = { selectedOutgoing = it },
                    )
                }
            }
            if (state.items.isEmpty()) {
                StatusText(
                    stringResource(
                        when {
                            state.loading -> R.string.messages_loading
                            state.isConnected -> R.string.messages_no_messages
                            else -> R.string.messages_no_internet
                        },
                    ),
                )
            }
        }
    }

    menuTrack?.let { track ->
        TrackMenuSheet(track, cover(track), trackMenu(track), onDismiss = { menuTrack = null })
    }
    selectedOutgoing?.let { outgoing ->
        OutgoingMenuSheet(outgoing, onAction, onDismiss = { selectedOutgoing = null })
    }
    if (pickingTracks) {
        TrackPickerSheet(
            limit = ChatViewModel.MAX_ATTACHMENTS - state.attachments.size,
            onAttach = {
                onAction(ChatAction.Attach(it))
                pickingTracks = false
            },
            onDismiss = { pickingTracks = false },
        )
    }
}

@Composable
private fun ChatItemContent(
    item: ChatItem,
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onOutgoingClick: (OutgoingMessage) -> Unit,
) {
    val onTrackClick = { track: Track -> onAction(ChatAction.PlayTrack(track)) }
    val onVoiceClick = { voice: VoiceMessage -> onAction(ChatAction.ToggleVoice(voice)) }
    when (item) {
        is ChatItem.DayHeader -> DayHeader(dayTitle(item.date))
        is ChatItem.MessageRow -> MessageBubble(
            content = BubbleContent(
                senderName = item.senderName,
                text = item.message.text,
                attachments = item.message.attachments,
                isOutgoing = item.message.isOutgoing,
                footer = MessageDates.timeOnly(item.message.date),
                failed = false,
                isRead = item.isRead.takeIf { item.message.isOutgoing },
            ),
            playback = state.playback,
            onTrackClick = onTrackClick,
            onTrackLongClick = onTrackMenu,
            onVoiceClick = onVoiceClick,
        )
        is ChatItem.OutgoingRow -> {
            val outgoing = item.outgoing
            MessageBubble(
                content = BubbleContent(
                    senderName = null,
                    text = outgoing.text,
                    attachments = outgoing.attachments.map(MessageAttachment::Audio),
                    isOutgoing = true,
                    footer = when {
                        outgoing.failure != null -> stringResource(R.string.messages_not_sent, outgoing.failure)
                        state.isConnected -> stringResource(R.string.messages_sending)
                        else -> stringResource(R.string.messages_waiting_for_network)
                    },
                    failed = outgoing.failure != null,
                    isRead = null,
                ),
                playback = state.playback,
                onTrackClick = onTrackClick,
                onTrackLongClick = onTrackMenu,
                onVoiceClick = onVoiceClick,
                modifier = Modifier.clickable { onOutgoingClick(outgoing) },
            )
        }
    }
}

@Composable
private fun OutgoingMenuSheet(outgoing: OutgoingMessage, onAction: (ChatAction) -> Unit, onDismiss: () -> Unit) {
    val items = buildList {
        if (outgoing.failure != null) add(MenuItem(R.string.messages_retry, Icons.Outlined.Refresh) { onAction(ChatAction.Retry(outgoing)) })
        add(MenuItem(R.string.track_delete, Icons.Outlined.Delete, destructive = true) { onAction(ChatAction.Delete(outgoing)) })
    }
    MenuSheet(items, onDismiss) {
        Text(
            outgoing.failure ?: stringResource(R.string.messages_queued),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
