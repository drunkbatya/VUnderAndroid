package com.drunkbatya.vunder.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.cache.AudioCache
import com.drunkbatya.vunder.core.session.SessionStore
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import com.drunkbatya.vunder.core.settings.UserSettings
import com.drunkbatya.vunder.core.update.UpdateState
import com.drunkbatya.vunder.core.update.Updater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings,
    val isCheckingForUpdates: Boolean,
    val savedTracksSize: Long?,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updater: Updater,
    private val sessionStore: SessionStore,
    private val cache: AudioCache,
) : ViewModel() {
    private val savedTracksSize = MutableStateFlow<Long?>(null)

    val state: StateFlow<SettingsUiState?> = combine(settingsRepository.settings, updater.state, savedTracksSize) { settings, updateState, size ->
        SettingsUiState(settings, isCheckingForUpdates = updateState == UpdateState.Checking, savedTracksSize = size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshSavedTracksSize() {
        viewModelScope.launch { savedTracksSize.value = cache.totalSize() }
    }

    fun removeSavedTracks() {
        Log.i(TAG, "remove all saved tracks")
        viewModelScope.launch {
            cache.clear()
            savedTracksSize.value = cache.totalSize()
        }
    }

    fun setToggle(toggle: Toggle, enabled: Boolean) {
        Log.i(TAG, "setting ${toggle.name}=$enabled")
        viewModelScope.launch { settingsRepository.set(toggle, enabled) }
    }

    fun checkForUpdates() = updater.checkNow()

    fun signOut() {
        Log.i(TAG, "sign out")
        viewModelScope.launch { sessionStore.clear() }
    }

    private companion object {
        const val TAG = "Settings"
    }
}
