package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.Conversation
import com.drunkbatya.vunder.ui.components.LoadMoreEffect
import com.drunkbatya.vunder.ui.components.StatusText

@Composable
internal fun ConversationsRoute(
    onSelect: (Conversation) -> Unit,
    onCompose: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConversationsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onSelect = onSelect,
        onCompose = onCompose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationsScreen(
    state: ConversationsUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSelect: (Conversation) -> Unit,
    onCompose: () -> Unit,
) {
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title)) },
                actions = {
                    IconButton(onClick = onCompose) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.messages_compose))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.conversations, key = { it.peerId }) { conversation ->
                    ConversationRow(conversation, state.outgoingByPeer[conversation.peerId]) { onSelect(conversation) }
                }
            }
            if (state.conversations.isEmpty() && !state.refreshing) {
                StatusText(stringResource(if (state.isConnected) R.string.messages_no_conversations else R.string.messages_no_internet))
            }
        }
    }
    LoadMoreEffect(listState, threshold = 5, retryKey = state.refreshing, onLoadMore = onLoadMore)
}
