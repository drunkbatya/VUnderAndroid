package com.drunkbatya.vunder.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.drunkbatya.vunder.BuildConfig
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppRelease(val tag: String, val notes: String?, val apkUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val reason: String) : UpdateState
    data class Available(val release: AppRelease, val allowsSkip: Boolean) : UpdateState
}

@Singleton
class Updater @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val httpClient: OkHttpClient,
) {
    private val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("update") }
    private val installedVersion = ReleaseVersion.parse(BuildConfig.VERSION_NAME) ?: ReleaseVersion(emptyList())
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    fun checkOnLaunch() {
        if (job?.isActive == true) return
        job = scope.launch {
            val preferences = dataStore.data.first()
            val lastCheck = preferences[lastCheckKey]
            if (lastCheck != null && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL.inWholeMilliseconds) return@launch
            val release = try {
                newerRelease()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "launch check failed: ${e.message}")
                return@launch
            } ?: return@launch
            if (release.tag == preferences[skippedTagKey]) {
                Log.i(TAG, "release ${release.tag} skipped by user")
                return@launch
            }
            _state.compareAndSet(UpdateState.Idle, UpdateState.Available(release, allowsSkip = true))
        }
    }

    fun checkNow() {
        Log.i(TAG, "manual update check")
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                newerRelease()?.let { UpdateState.Available(it, allowsSkip = false) } ?: UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "manual check failed: ${e.message}")
                UpdateState.Failed(e.message ?: e.javaClass.name)
            }
        }
    }

    fun skip(release: AppRelease) {
        Log.i(TAG, "skipping ${release.tag}")
        _state.value = UpdateState.Idle
        scope.launch { dataStore.edit { it[skippedTagKey] = release.tag } }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    fun download(release: AppRelease) {
        _state.value = UpdateState.Idle
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        directory?.listFiles { file -> file.extension == "apk" }?.forEach { it.delete() }
        val request = DownloadManager.Request(release.apkUrl.toUri())
            .setTitle("${context.getString(R.string.app_name)} ${release.tag}")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "VUnder-${release.tag}.apk")
        val downloadId = downloadManager.enqueue(request)
        Log.i(TAG, "downloading ${release.apkUrl} id=$downloadId")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                context.unregisterReceiver(this)
                install(downloadId)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun install(downloadId: Long) {
        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
        if (apkUri == null) {
            val reason = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) else null
            }
            Log.e(TAG, "download id=$downloadId failed reason=$reason")
            _state.value = UpdateState.Failed("download id=$downloadId reason=$reason")
            return
        }
        Log.i(TAG, "installing $apkUri")
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private suspend fun newerRelease(): AppRelease? {
        val release = latestRelease()
        dataStore.edit { it[lastCheckKey] = System.currentTimeMillis() }
        Log.i(TAG, "latest release ${release.tagName}, installed $installedVersion")
        val version = ReleaseVersion.parse(release.tagName) ?: throw IOException("release tag ${release.tagName} is not a version")
        if (version <= installedVersion) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: throw IOException("release ${release.tagName} has no apk asset")
        return AppRelease(release.tagName, release.body?.trim()?.takeIf { it.isNotEmpty() }, apk.downloadUrl)
    }

    private suspend fun latestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST_RELEASE_URL).header("Accept", "application/vnd.github+json").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("$LATEST_RELEASE_URL http=${response.code}")
            json.decodeFromString<GitHubRelease>(response.body.string())
        }
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(val name: String, @SerialName("browser_download_url") val downloadUrl: String)

    private companion object {
        const val TAG = "Updater"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/drunkbatya/VUnderAndroid/releases/latest"
        val CHECK_INTERVAL = 1.days
        val lastCheckKey = longPreferencesKey("last_check")
        val skippedTagKey = stringPreferencesKey("skipped_tag")
    }
}
