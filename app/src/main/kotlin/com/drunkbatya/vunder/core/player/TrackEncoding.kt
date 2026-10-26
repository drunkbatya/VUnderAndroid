package com.drunkbatya.vunder.core.player

import android.content.Context
import android.text.format.Formatter
import android.util.Log
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.cache.AudioCache
import com.drunkbatya.vunder.core.hls.HlsDownloader
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.VkClientIdentity
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class TrackEncoding(
    val sizeBytes: Long,
    val bitrateKbps: Int,
    val isLocal: Boolean,
    val isHls: Boolean,
) {
    fun formatted(context: Context): String {
        val size = Formatter.formatFileSize(context, sizeBytes)
        return buildList {
            add(context.getString(R.string.track_file_bitrate, bitrateKbps))
            add(if (isHls) "~$size" else size)
            if (isHls) add(context.getString(R.string.track_file_hls))
            if (isLocal) add(context.getString(R.string.track_file_saved))
        }.joinToString(", ")
    }
}

@Singleton
class TrackEncodingProbe @Inject constructor(
    httpClient: OkHttpClient,
    private val cache: AudioCache,
) {
    private val probeClient = httpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val hlsDownloader = HlsDownloader(probeClient)

    suspend fun probe(track: Track): TrackEncoding? {
        if (track.duration <= 0) return null
        val localSize = withContext(Dispatchers.IO) { cache.localFile(track)?.length() ?: 0 }
        if (localSize > 0) return TrackEncoding(localSize, bitrate(localSize, track.duration), isLocal = true, isHls = false)
        val url = track.url?.toHttpUrlOrNull() ?: return null
        return try {
            if (track.isHls) hlsEncoding(url, track) else remoteEncoding(url, track)
        } catch (failure: IOException) {
            Log.w(TAG, "encoding probe for ${track.storageId} failed: ${failure.message}")
            null
        }
    }

    private suspend fun hlsEncoding(url: HttpUrl, track: Track): TrackEncoding? {
        val bandwidth = hlsDownloader.maxBandwidth(url)?.takeIf { it > 0 } ?: return null
        return TrackEncoding(bandwidth.toLong() * track.duration / 8, bandwidth / 1000, isLocal = false, isHls = true)
    }

    private suspend fun remoteEncoding(url: HttpUrl, track: Track): TrackEncoding? {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", VkClientIdentity.userAgent(VkClientIdentity.AppVersion.General))
            .build()
        val (status, size) = withContext(Dispatchers.IO) {
            probeClient.newCall(request).execute().use { it.code to (it.header("Content-Length")?.toLongOrNull() ?: -1) }
        }
        if (size <= 0) {
            Log.w(TAG, "no content length for ${track.storageId} http=$status")
            return null
        }
        return TrackEncoding(size, bitrate(size, track.duration), isLocal = false, isHls = false)
    }

    private fun bitrate(bytes: Long, duration: Int): Int = (bytes * 8 / duration / 1000).toInt()

    private companion object {
        const val TAG = "TrackEncoding"
    }
}
