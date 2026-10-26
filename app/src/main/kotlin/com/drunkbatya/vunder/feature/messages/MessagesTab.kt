package com.drunkbatya.vunder.feature.messages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import com.drunkbatya.vunder.ui.navigation.StackNavDisplay

@Composable
fun MessagesTab(viewModel: MessagesViewModel) {
    val chatRequests by viewModel.chatRequests.collectAsStateWithLifecycle()
    StackNavDisplay(
        backStack = viewModel.backStack,
        onBack = viewModel::pop,
        entryProvider = entryProvider {
            entry<MessagesRoute.Conversations> {
                ConversationsRoute(
                    onSelect = { viewModel.openChat(it.peerId, it.title) },
                    onCompose = viewModel::showFriends,
                )
            }
            entry<MessagesRoute.Friends> {
                FriendsRoute(onSelect = { viewModel.replaceFriendsWithChat(it.id, it.name) }, onBack = viewModel::pop)
            }
            entry<MessagesRoute.Chat> { route ->
                ChatRoute(
                    route = route,
                    request = chatRequests[route.peerId],
                    onRequestHandled = { viewModel.consumeChatRequest(route.peerId) },
                    onBack = viewModel::pop,
                )
            }
        },
    )
}
