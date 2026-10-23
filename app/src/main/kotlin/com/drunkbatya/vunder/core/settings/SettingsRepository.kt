package com.drunkbatya.vunder.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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

data class UserSettings(
    val historyEnabled: Boolean = true,
    val cacheEnabled: Boolean = true,
    val cacheOnlyMine: Boolean = true,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val preferHls: Boolean = true,
    val markAsRead: Boolean = true,
    val longPollEnabled: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
    private val defaults = UserSettings()

    val settings: Flow<UserSettings> = dataStore.data.map { it.toUserSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setHistoryEnabled(enabled: Boolean) = set(Keys.historyEnabled, enabled)
    suspend fun setCacheEnabled(enabled: Boolean) = set(Keys.cacheEnabled, enabled)
    suspend fun setCacheOnlyMine(enabled: Boolean) = set(Keys.cacheOnlyMine, enabled)
    suspend fun setShuffle(enabled: Boolean) = set(Keys.shuffle, enabled)
    suspend fun setRepeatMode(mode: RepeatMode) = set(Keys.repeatMode, mode.name)
    suspend fun setPreferHls(enabled: Boolean) = set(Keys.preferHls, enabled)
    suspend fun setMarkAsRead(enabled: Boolean) = set(Keys.markAsRead, enabled)
    suspend fun setLongPollEnabled(enabled: Boolean) = set(Keys.longPollEnabled, enabled)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun Preferences.toUserSettings() = UserSettings(
        historyEnabled = this[Keys.historyEnabled] ?: defaults.historyEnabled,
        cacheEnabled = this[Keys.cacheEnabled] ?: defaults.cacheEnabled,
        cacheOnlyMine = this[Keys.cacheOnlyMine] ?: defaults.cacheOnlyMine,
        shuffle = this[Keys.shuffle] ?: defaults.shuffle,
        repeatMode = this[Keys.repeatMode]?.let { name -> RepeatMode.entries.firstOrNull { it.name == name } } ?: defaults.repeatMode,
        preferHls = this[Keys.preferHls] ?: defaults.preferHls,
        markAsRead = this[Keys.markAsRead] ?: defaults.markAsRead,
        longPollEnabled = this[Keys.longPollEnabled] ?: defaults.longPollEnabled,
    )

    private object Keys {
        val historyEnabled = booleanPreferencesKey("history_enabled")
        val cacheEnabled = booleanPreferencesKey("cache_enabled")
        val cacheOnlyMine = booleanPreferencesKey("cache_only_mine")
        val shuffle = booleanPreferencesKey("player_shuffle")
        val repeatMode = stringPreferencesKey("player_repeat")
        val preferHls = booleanPreferencesKey("prefer_hls")
        val markAsRead = booleanPreferencesKey("messages_mark_as_read")
        val longPollEnabled = booleanPreferencesKey("long_poll")
    }
}
