package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.Profile
import com.drunkbatya.vunder.ui.components.BackTopBar
import com.drunkbatya.vunder.ui.components.LoadMoreEffect
import com.drunkbatya.vunder.ui.components.SearchField
import com.drunkbatya.vunder.ui.components.StatusText

@Composable
internal fun FriendsRoute(
    onSelect: (Profile) -> Unit,
    onBack: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FriendsScreen(state, viewModel.searchField, onLoadMore = viewModel::loadPage, onSelect = onSelect, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendsScreen(
    state: FriendsUiState,
    searchField: TextFieldState,
    onLoadMore: () -> Unit,
    onSelect: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(stringResource(R.string.messages_friends), onBack)
        },
    ) { padding ->
        FriendsContent(state, searchField, onLoadMore, onSelect, Modifier.padding(padding))
    }
}

@Composable
internal fun FriendsContent(
    state: FriendsUiState,
    searchField: TextFieldState,
    onLoadMore: () -> Unit,
    onSelect: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        SearchField(searchField, stringResource(R.string.messages_search_friends))
        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.friends, key = { it.id }) { profile ->
                    ProfileRow(profile) { onSelect(profile) }
                }
            }
            if (state.friends.isEmpty()) {
                StatusText(
                    when {
                        state.offline -> stringResource(R.string.messages_no_internet)
                        state.failure != null -> state.failure
                        state.loading || state.total == null -> stringResource(R.string.messages_loading)
                        state.query.isEmpty() -> stringResource(R.string.messages_no_friends)
                        else -> stringResource(R.string.messages_nobody_matches, state.query)
                    },
                )
            }
        }
    }
    LoadMoreEffect(listState, threshold = 10, onLoadMore = onLoadMore)
}

