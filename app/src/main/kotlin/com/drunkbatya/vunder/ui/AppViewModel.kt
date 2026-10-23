package com.drunkbatya.vunder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class SignedIn(val userId: Long) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(sessionStore: SessionStore) : ViewModel() {
    val state: StateFlow<AppState> = sessionStore.session
        .map { session -> session?.let { AppState.SignedIn(it.userId) } ?: AppState.SignedOut }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)
}
