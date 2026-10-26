package com.drunkbatya.vunder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class SignedIn(val userId: Long) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(sessionStore: SessionStore) : ViewModel() {
    private var account: AccountViewModels? = null

    val state: StateFlow<AppState> = sessionStore.session
        .map { session -> session?.let { AppState.SignedIn(it.userId) } ?: AppState.SignedOut }
        .distinctUntilChanged()
        .onEach { if (it !is AppState.SignedIn || it.userId != account?.userId) releaseAccount() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    fun accountViewModelStore(userId: Long): ViewModelStore =
        account?.takeIf { it.userId == userId }?.store ?: AccountViewModels(userId, ViewModelStore()).also { account = it }.store

    override fun onCleared() = releaseAccount()

    private fun releaseAccount() {
        account?.store?.clear()
        account = null
    }

    private class AccountViewModels(val userId: Long, val store: ViewModelStore)
}
