package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.Conversation
import com.drunkbatya.vunder.core.messaging.MessageStore
import com.drunkbatya.vunder.ui.components.BackButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConversationPickerViewModel @Inject constructor(store: MessageStore) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> =
        store.conversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPickerSheet(
    onPick: (peerId: Long, title: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ConversationPickerViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    var showingFriends by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showingFriends) BackButton { showingFriends = false }
                Text(
                    stringResource(if (showingFriends) R.string.messages_friends else R.string.messages_send_to),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = if (showingFriends) 4.dp else 16.dp),
                )
            }
            if (showingFriends) {
                val friendsViewModel: FriendsViewModel = hiltViewModel(key = FRIENDS_VIEW_MODEL_KEY)
                val friendsState by friendsViewModel.state.collectAsStateWithLifecycle()
                FriendsContent(
                    state = friendsState,
                    searchField = friendsViewModel.searchField,
                    onLoadMore = friendsViewModel::loadPage,
                    onSelect = { onPick(it.id, it.name) },
                )
            } else {
                ConversationPickerList(conversations, onFriends = { showingFriends = true }, onPick = onPick)
            }
        }
    }
}

@Composable
private fun ConversationPickerList(
    conversations: List<Conversation>,
    onFriends: () -> Unit,
    onPick: (peerId: Long, title: String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "friends") {
            ListRow(onFriends) {
                Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(44.dp).padding(8.dp))
                Text(stringResource(R.string.messages_friends), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (conversations.isNotEmpty()) {
            item(key = "recent") {
                Text(
                    stringResource(R.string.messages_recent).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
        }
        items(conversations, key = { it.peerId }) { conversation ->
            ConversationRow(conversation, outgoing = null) { onPick(conversation.peerId, conversation.title) }
        }
    }
}

private const val FRIENDS_VIEW_MODEL_KEY = "conversation_picker_friends"
