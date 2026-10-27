package com.drunkbatya.vunder.feature.messages

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.messaging.MessagesApi
import com.drunkbatya.vunder.core.messaging.Profile
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.ui.components.trimmedText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val query: String = "",
    val friends: List<Profile> = emptyList(),
    val total: Int? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val failure: String? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val api: MessagesApi,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    val searchField = TextFieldState()

    private var pageJob: Job? = null

    init {
        viewModelScope.launch {
            searchField.trimmedText().collectLatest { query ->
                if (query != _state.value.query) delay(SEARCH_DEBOUNCE)
                pageJob?.cancel()
                _state.value = FriendsUiState(query = query)
                loadPage()
            }
        }
    }

    fun loadPage() {
        val current = _state.value
        if (pageJob?.isActive == true || current.total?.let { current.friends.size >= it } == true) return
        if (!network.isConnected) {
            _state.update { it.copy(offline = true) }
            return
        }
        _state.update { it.copy(loading = true, offline = false, failure = null) }
        val query = current.query
        val offset = current.friends.size
        pageJob = viewModelScope.launch {
            try {
                val page = if (query.isEmpty()) api.friends(offset) else api.searchFriends(query, offset)
                _state.update { state ->
                    val known = state.friends.mapTo(HashSet()) { it.id }
                    state.copy(friends = state.friends + page.friends.filter { it.id !in known }, total = page.total, loading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "friends query='$query' offset=$offset failed: ${e.message}")
                _state.update { it.copy(loading = false, failure = e.message ?: e.javaClass.name) }
            }
        }
    }

    private companion object {
        const val TAG = "Friends"
        val SEARCH_DEBOUNCE = 300.milliseconds
    }
}
