package com.drunkbatya.vunder.core.session

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.drunkbatya.vunder.core.network.VkClientIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: KeystoreCipher,
) {
    private val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("session") }

    val session: Flow<Session?> = dataStore.data.map { preferences ->
        preferences[Keys.session]?.let(cipher::decrypt)?.let { Json.decodeFromString<Session>(it) }
    }

    suspend fun current(): Session? = session.first()

    suspend fun save(session: Session) {
        dataStore.edit { it[Keys.session] = cipher.encrypt(Json.encodeToString(session)) }
        Log.i(TAG, "session saved user_id=${session.userId}")
    }

    suspend fun updateAccessToken(token: String) {
        current()?.let { save(it.copy(accessToken = token)) }
    }

    suspend fun trustedHash(): String? = dataStore.data.first()[Keys.trustedHash]?.let(cipher::decrypt)

    suspend fun saveTrustedHash(hash: String) {
        dataStore.edit { it[Keys.trustedHash] = cipher.encrypt(hash) }
    }

    suspend fun deviceId(): String {
        dataStore.data.first()[Keys.deviceId]?.let { return it }
        var deviceId = ""
        dataStore.edit { preferences ->
            deviceId = preferences[Keys.deviceId] ?: VkClientIdentity.makeDeviceId().also {
                preferences[Keys.deviceId] = it
                Log.i(TAG, "device id generated $it")
            }
        }
        return deviceId
    }

    suspend fun clear() {
        dataStore.edit {
            it.remove(Keys.session)
            it.remove(Keys.trustedHash)
        }
        Log.i(TAG, "session cleared")
    }

    private object Keys {
        val session = stringPreferencesKey("session")
        val trustedHash = stringPreferencesKey("trusted_hash")
        val deviceId = stringPreferencesKey("device_id")
    }

    private companion object {
        const val TAG = "SessionStore"
    }
}
