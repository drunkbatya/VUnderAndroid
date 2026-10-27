package com.drunkbatya.vunder.core.cache

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
            cache.materialize(job.track, partFile) { fraction ->
                val newPercent = (fraction * 100).toInt()
                if (newPercent == percent) return@materialize
                percent = newPercent
                _jobs.update { jobs ->
                    jobs.map { if (it.id == job.id && it.state is DownloadState.Running) it.copy(state = DownloadState.Running(fraction)) else it }
                }
            }
            when (job.kind) {
                DownloadKind.Cache -> cache.install(job.track, partFile)
                DownloadKind.MusicFolder -> exporter.placeInMusicFolder(partFile, job.track)
            }
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

    private fun setState(id: Long, state: DownloadState) {
        _jobs.update { jobs -> jobs.map { if (it.id == id) it.copy(state = state) else it } }
    }

    private companion object {
        const val TAG = "DownloadCenter"
    }
}
