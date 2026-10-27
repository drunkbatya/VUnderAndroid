package com.drunkbatya.vunder.core.cache

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import com.drunkbatya.vunder.core.di.ApplicationScope
import com.drunkbatya.vunder.core.hls.HlsDownloader
import com.drunkbatya.vunder.core.hls.HlsException
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.model.TrackKey
import com.drunkbatya.vunder.core.music.TrackLibrary
import com.drunkbatya.vunder.core.network.VkClientIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadException(message: String) : IOException(message)

@Singleton
class AudioCache @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
    httpClient: OkHttpClient,
    private val library: TrackLibrary,
) {
    private val directory = File(context.noBackupFilesDir, "audio_cache")
    private val downloadClient = httpClient.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val hlsDownloader = HlsDownloader(downloadClient)

    val cachedKeys: StateFlow<Set<TrackKey>> = library.cachedKeys.stateIn(scope, SharingStarted.Eagerly, emptySet())

    init {
        scope.launch {
            try {
                reconcile()
            } catch (failure: SQLiteException) {
                Log.e(TAG, "reconcile failed: ${failure.message}")
            }
        }
    }

    fun isCached(track: Track): Boolean = track.key in cachedKeys.value

    fun localFile(track: Track): File? = audioFile(track).takeIf { isCached(track) }

    fun localCover(track: Track): File? = coverFile(track).takeIf { track.coverUrl != null && isCached(track) }

    suspend fun materialize(track: Track, destination: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        destination.delete()
        val local = audioFile(track).takeIf(File::exists)
        if (local != null) {
            local.copyTo(destination)
            onProgress(1f)
            return@withContext
        }
        val url = track.url?.toHttpUrlOrNull() ?: throw DownloadException("track ${track.storageId} has no url")
        if (track.isHls) {
            if (hlsDownloader.download(url, destination, onProgress) <= 0) throw HlsException("HLS download for ${track.storageId} wrote 0 bytes")
            return@withContext
        }
        downloadClient.newCall(request(url.toString())).execute().use { response ->
            if (response.code != 200) throw DownloadException("track ${track.storageId} download http=${response.code}")
            val expected = response.body.contentLength()
            var received = 0L
            response.body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val chunk = ByteArray(CHUNK_BYTES)
                    while (true) {
                        ensureActive()
                        val count = input.read(chunk)
                        if (count < 0) break
                        output.write(chunk, 0, count)
                        received += count
                        if (expected > 0) onProgress(received.toFloat() / expected)
                    }
                }
            }
            if (received <= 0 || (expected > 0 && received != expected)) {
                throw DownloadException("track ${track.storageId} download incomplete: $received of $expected bytes")
            }
        }
        onProgress(1f)
    }

    suspend fun install(track: Track, source: File) {
        val destination = audioFile(track)
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        storeCover(track)
        library.markCached(track, System.currentTimeMillis())
        Log.i(TAG, "installed ${track.storageId} ${destination.length()} bytes")
    }

    suspend fun remove(track: Track) {
        withContext(Dispatchers.IO) {
            audioFile(track).delete()
            coverFile(track).delete()
        }
        library.markCached(track, null)
        Log.i(TAG, "removed ${track.storageId}")
    }

    suspend fun clear() {
        val files = withContext(Dispatchers.IO) { directory.listFiles().orEmpty().onEach(File::delete) }
        library.clearCacheMarks()
        Log.i(TAG, "cleared ${files.size} files")
    }

    suspend fun totalSize(): Long = withContext(Dispatchers.IO) { directory.listFiles().orEmpty().sumOf(File::length) }

    private suspend fun reconcile() {
        val marked = library.currentCachedKeys().associateBy { "${it.ownerId}_${it.id}" }
        val files = withContext(Dispatchers.IO) { directory.listFiles().orEmpty().toList() }
        val present = files.filter { it.extension == AUDIO_EXTENSION }.map { it.nameWithoutExtension }.toSet()
        val missing = marked.filterKeys { it !in present }
        missing.keys.forEach { Log.w(TAG, "file missing for $it, unmarking") }
        if (missing.isNotEmpty()) library.unmarkCached(missing.values)
        val cached = present intersect marked.keys
        withContext(Dispatchers.IO) {
            files.filter { it.nameWithoutExtension !in cached }.forEach { file ->
                if (file.extension == AUDIO_EXTENSION) Log.w(TAG, "orphan file ${file.nameWithoutExtension}, deleting")
                file.delete()
            }
        }
        Log.i(TAG, "reconciled cached=${cached.size}")
    }

    private suspend fun storeCover(track: Track) {
        val url = track.coverUrl ?: return
        val destination = coverFile(track)
        try {
            val size = withContext(Dispatchers.IO) {
                if (destination.exists()) return@withContext null
                downloadClient.newCall(request(url)).execute().use { response ->
                    val bytes = response.body.bytes()
                    if (response.code != 200 || bytes.isEmpty()) throw DownloadException("cover for ${track.storageId} http=${response.code}")
                    val partFile = File(directory, "${destination.name}.part")
                    partFile.writeBytes(bytes)
                    Files.move(partFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    bytes.size
                }
            } ?: return
            Log.i(TAG, "cover stored for ${track.storageId} $size bytes")
        } catch (failure: IOException) {
            Log.e(TAG, "cover for ${track.storageId} failed: ${failure.message}")
        }
    }

    private fun audioFile(track: Track) = File(directory, "${track.storageId}.$AUDIO_EXTENSION")

    private fun coverFile(track: Track) = File(directory, "${track.storageId}.jpg")

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", VkClientIdentity.userAgent(VkClientIdentity.AppVersion.General))
        .build()

    private companion object {
        const val TAG = "AudioCache"
        const val AUDIO_EXTENSION = "mp3"
        const val CHUNK_BYTES = 65_536
    }
}
