package com.drunkbatya.vunder.core.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class RepeatMode {
    Off,
    All,
    One;

    val next: RepeatMode
        get() = entries[(ordinal + 1) % entries.size]
}

enum class Toggle(storageKey: String, val default: Boolean) {
    History("history_enabled", true),
    Cache("cache_enabled", true),
    CacheOnlyMine("cache_only_mine", true),
    Shuffle("player_shuffle", false),
    PreferHls("prefer_hls", true),
    MarkAsRead("messages_mark_as_read", true),
    LongPoll("long_poll", true);

    val key = booleanPreferencesKey(storageKey)
}

data class UserSettings(private val enabled: Set<Toggle>, val repeatMode: RepeatMode) {
    operator fun get(toggle: Toggle): Boolean = toggle in enabled
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
    private val repeatModeKey = stringPreferencesKey("player_repeat")

    val settings: Flow<UserSettings> = dataStore.data.map { preferences ->
        UserSettings(
            enabled = Toggle.entries.filterTo(mutableSetOf()) { preferences[it.key] ?: it.default },
            repeatMode = RepeatMode.entries.firstOrNull { it.name == preferences[repeatModeKey] } ?: RepeatMode.Off,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun set(toggle: Toggle, enabled: Boolean) {
        dataStore.edit { it[toggle.key] = enabled }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        dataStore.edit { it[repeatModeKey] = mode.name }
    }
}
