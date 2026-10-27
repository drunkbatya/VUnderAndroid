package com.drunkbatya.vunder.core.hls

import android.util.Log
import com.drunkbatya.vunder.core.network.VkClientIdentity
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class HlsDownloader(private val httpClient: OkHttpClient) {
    suspend fun download(playlistUrl: HttpUrl, destination: File, onProgress: (Float) -> Unit): Long = withContext(Dispatchers.IO) {
        val segments = mediaSegments(playlistUrl)
        val name = playlistUrl.pathSegments.lastOrNull()
        Log.i(TAG, "hls $name: ${segments.size} segments")
        val demuxer = MpegTsDemuxer()
        val keys = mutableMapOf<HttpUrl, ByteArray>()
        var written = 0L
        destination.outputStream().buffered().use { output ->
            segments.forEachIndexed { index, segment ->
                ensureActive()
                var bytes = fetchWithRetry(segment.url)
                val keyUrl = segment.key.url
                if (keyUrl != null) {
                    val key = keys.getOrPut(keyUrl) {
                        fetchWithRetry(keyUrl).also { if (it.size != AES_BLOCK_BYTES) throw HlsException("HLS key has ${it.size} bytes: $keyUrl") }
                    }
                    bytes = decrypt(bytes, key, segment.key.iv ?: ByteArray(AES_BLOCK_BYTES))
                }
                val audio = demuxer.demux(bytes)
                if (index == 0 && demuxer.audioStreamType == null) throw HlsException("HLS transport stream has no audio PID")
                output.write(audio)
                written += audio.size
                onProgress((index + 1).toFloat() / segments.size)
            }
        }
        Log.i(TAG, "hls $name: stream type ${demuxer.audioStreamType?.let { "0x%02x".format(it) } ?: "?"}, $written bytes")
        written
    }

    suspend fun maxBandwidth(playlistUrl: HttpUrl): Int? = withContext(Dispatchers.IO) {
        (fetchPlaylist(playlistUrl) as? HlsPlaylist.Master)?.variants?.maxOfOrNull { it.bandwidth }
    }

    private suspend fun mediaSegments(playlistUrl: HttpUrl): List<HlsSegment> =
        when (val playlist = fetchPlaylist(playlistUrl)) {
            is HlsPlaylist.Media -> playlist.segments
            is HlsPlaylist.Master -> {
                val best = playlist.variants.maxBy { it.bandwidth }
                (fetchPlaylist(best.url) as? HlsPlaylist.Media)?.segments ?: throw HlsException("HLS playlist has no entries: ${best.url}")
            }
        }

    private suspend fun fetchPlaylist(url: HttpUrl): HlsPlaylist = HlsPlaylist.parse(fetchWithRetry(url).decodeToString(), url)

    private suspend fun fetchWithRetry(url: HttpUrl): ByteArray {
        var lastFailure: IOException? = null
        repeat(FETCH_ATTEMPTS) { attempt ->
            try {
                return fetch(url)
            } catch (failure: IOException) {
                lastFailure = failure
                Log.w(TAG, "hls fetch attempt ${attempt + 1} failed: ${failure.message}")
            }
        }
        throw lastFailure ?: HlsException("HLS fetch failed: $url")
    }

    private suspend fun fetch(url: HttpUrl): ByteArray = withContext(Dispatchers.IO) {
        ensureActive()
        val request = Request.Builder().url(url).header("User-Agent", VkClientIdentity.userAgent(VkClientIdentity.AppVersion.General)).build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code != 200) throw HlsException("HLS fetch failed http=${response.code}: $url")
            response.body.bytes()
        }
    }

    private fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = try {
        Cipher.getInstance("AES/CBC/PKCS5Padding")
            .apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
            .doFinal(ciphertext)
    } catch (failure: GeneralSecurityException) {
        throw HlsException("HLS segment decrypt failed: ${failure.message}")
    }

    private companion object {
        const val TAG = "HlsDownloader"
        const val AES_BLOCK_BYTES = 16
        const val FETCH_ATTEMPTS = 3
    }
}
