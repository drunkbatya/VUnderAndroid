package com.drunkbatya.vunder.core.cache

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.music.TrackUrlRefresher
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DownloadKind(@StringRes val title: Int) {
    Cache(R.string.download_kind_cache),
    MusicFolder(R.string.download_kind_music_folder),
    Icon(R.string.download_kind_icon),
    Cover(R.string.download_kind_cover),
}

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val fraction: Float?) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Cancelled : DownloadState

    val isFinished: Boolean
        get() = this is Done || this is Failed || this is Cancelled
}

data class DownloadJob(
    val id: Long,
    val track: Track,
    val kind: DownloadKind,
    val state: DownloadState = DownloadState.Queued,
)

@MainThread
@Singleton
class DownloadCenter @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    private val cache: AudioCache,
    private val exporter: TrackExporter,
    private val urlRefresher: TrackUrlRefresher,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate)
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val workers = mutableMapOf<Long, Job>()
    private var nextId = 0L
    private var running = false

    private val activeCount: Int
        get() = jobs.value.count { !it.state.isFinished }

    fun enqueue(track: Track, kind: DownloadKind) {
        if (jobs.value.any { it.track.isSame(track) && it.kind == kind && !it.state.isFinished }) return
        _jobs.update { it + DownloadJob(nextId++, track, kind) }
        Log.i(TAG, "queued $kind ${track.storageId}, active=$activeCount")
        runNext()
    }

    suspend fun saveCover(track: Track, isLarge: Boolean): ByteArray {
        val kind = if (isLarge) DownloadKind.Cover else DownloadKind.Icon
        val url = track.coverUrl(isLarge) ?: throw DownloadException("track ${track.storageId} has no $kind url")
        val id = startImageJob(track, kind)
        val image = try {
            cache.downloadCover(url)
        } catch (cancellation: CancellationException) {
            _jobs.update { jobs -> jobs.filterNot { it.id == id } }
            throw cancellation
        } catch (failure: IOException) {
            finishImageJob(id, kind, track, failure.message ?: failure.javaClass.simpleName)
            throw failure
        }
        val storeFailure = try {
            cache.storeCover(track, isLarge, image)
            null
        } catch (failure: IOException) {
            failure.message ?: failure.javaClass.simpleName
        }
        finishImageJob(id, kind, track, storeFailure)
        return image
    }

    fun isSavingCover(track: Track): Boolean = jobs.value.any { it.kind == DownloadKind.Cover && it.track.isSame(track) && !it.state.isFinished }

    private suspend fun startImageJob(track: Track, kind: DownloadKind): Long = withContext(Dispatchers.Main.immediate) {
        val job = DownloadJob(nextId++, track, kind, DownloadState.Running(null))
        _jobs.update { jobs -> jobs.filterNot { it.kind == kind && it.track.isSame(track) && it.state.isFinished } + job }
        Log.i(TAG, "started $kind ${track.storageId}")
        job.id
    }

    private fun finishImageJob(id: Long, kind: DownloadKind, track: Track, failure: String?) {
        if (failure != null) Log.e(TAG, "$kind ${track.storageId} failed: $failure")
        _jobs.update { jobs ->
            jobs.map { if (it.id == id && it.state is DownloadState.Running) it.copy(state = failure?.let(DownloadState::Failed) ?: DownloadState.Done) else it }
        }
    }

    fun cancel(job: DownloadJob) {
        val current = jobs.value.firstOrNull { it.id == job.id } ?: return
        if (current.state.isFinished) return
        workers.remove(job.id)?.cancel()
        setState(job.id, DownloadState.Cancelled)
        Log.i(TAG, "cancelled ${job.kind} ${job.track.storageId}")
        runNext()
    }

    fun cancelCacheJobs(track: Track) {
        jobs.value.filter { it.kind == DownloadKind.Cache && it.track.isSame(track) && !it.state.isFinished }.forEach(::cancel)
    }

    fun cancelAll() {
        jobs.value.filter { !it.state.isFinished }.forEach(::cancel)
    }

    fun clearFinished() {
        _jobs.update { jobs -> jobs.filter { !it.state.isFinished } }
    }

    private fun runNext() {
        if (running) return
        val job = jobs.value.firstOrNull { it.state == DownloadState.Queued } ?: return
        running = true
        setState(job.id, DownloadState.Running(null))
        workers[job.id] = scope.launch {
            try {
                run(job)
            } finally {
                workers.remove(job.id)
                running = false
                runNext()
            }
        }
    }

    private suspend fun run(job: DownloadJob) {
        if (job.kind == DownloadKind.Cache && cache.isCached(job.track)) {
            setState(job.id, DownloadState.Done)
            return
        }
        val partFile = withContext(Dispatchers.IO) { File.createTempFile("download", ".part", context.cacheDir) }
        try {
            var percent = -1
            val source = materializeWithFreshUrl(job.track, partFile) { fraction ->
                val newPercent = (fraction * 100).toInt()
                if (newPercent == percent) return@materializeWithFreshUrl
                percent = newPercent
                _jobs.update { jobs ->
                    jobs.map { if (it.id == job.id && it.state is DownloadState.Running) it.copy(state = DownloadState.Running(fraction)) else it }
                }
            }
            if (job.kind == DownloadKind.Cache) cache.install(source, partFile) else exporter.placeInMusicFolder(partFile, source)
            setState(job.id, DownloadState.Done)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            if (currentCoroutineContext().isActive) {
                Log.e(TAG, "${job.kind} ${job.track.storageId} failed: ${failure.message}")
                setState(job.id, DownloadState.Failed(failure.message ?: failure.javaClass.simpleName))
            }
        } finally {
            partFile.delete()
        }
    }

    private suspend fun materializeWithFreshUrl(track: Track, partFile: File, onProgress: (Float) -> Unit): Track {
        if (cache.isCached(track)) {
            cache.materialize(track, partFile, onProgress)
            return track
        }
        val stored = track.copy(url = urlRefresher.storedUrl(track))
        val preferHls = settingsRepository.current()[Toggle.PreferHls]
        val formatMatches = stored.url != null && stored.isHls == preferHls
        val source = if (formatMatches) stored else freshTrack(stored, "hls=${stored.isHls} preferHls=$preferHls") ?: stored
        try {
            cache.materialize(source, partFile, onProgress)
            return source
        } catch (failure: IOException) {
            if (source !== stored) throw failure
            val fresh = freshTrack(stored, failure.message.orEmpty()) ?: throw failure
            cache.materialize(fresh, partFile, onProgress)
            return fresh
        }
    }

    private suspend fun freshTrack(track: Track, reason: String): Track? {
        Log.i(TAG, "${track.storageId} requesting fresh url: $reason")
        return try {
            urlRefresher.freshUrl(track)?.let { track.copy(url = it) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.e(TAG, "${track.storageId} fresh url failed: ${failure.message}")
            null
        }
    }

    private fun setState(id: Long, state: DownloadState) {
        _jobs.update { jobs -> jobs.map { if (it.id == id) it.copy(state = state) else it } }
    }

    private companion object {
        const val TAG = "DownloadCenter"
    }
}
