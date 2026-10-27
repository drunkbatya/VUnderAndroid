package com.drunkbatya.vunder.core.messaging

import android.util.Log
import com.drunkbatya.vunder.core.network.VkClientIdentity
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

sealed interface LongPollEvent {
    data class NewMessage(val id: Long) : LongPollEvent
    data class FlagsSet(val id: Long, val peerId: Long, val mask: Long) : LongPollEvent
    data class IncomingRead(val peerId: Long, val messageId: Long) : LongPollEvent
    data class OutgoingRead(val peerId: Long, val messageId: Long) : LongPollEvent
}

sealed interface LongPollResponse {
    data class Events(val events: List<LongPollEvent>, val ts: Long) : LongPollResponse
    data class OutdatedTs(val ts: Long) : LongPollResponse
    data object KeyExpired : LongPollResponse
}

@Singleton
class LongPollClient @Inject constructor(httpClient: OkHttpClient) {
    private val httpClient = httpClient.newBuilder()
        .callTimeout((WAIT_SECONDS + 10).seconds.toJavaDuration())
        .readTimeout((WAIT_SECONDS + 10).seconds.toJavaDuration())
        .build()

    suspend fun check(server: LongPollServer, ts: Long): LongPollResponse {
        val url = server.url.toHttpUrl().newBuilder()
            .addQueryParameter("act", "a_check")
            .addQueryParameter("key", server.key)
            .addQueryParameter("ts", ts.toString())
            .addQueryParameter("wait", WAIT_SECONDS.toString())
            .addQueryParameter("mode", "2")
            .addQueryParameter("version", "3")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", VkClientIdentity.userAgent(VkClientIdentity.AppVersion.General))
            .build()
        val (status, body) = httpClient.newCall(request).await()
        if (status != 200) throw IOException("long poll http=$status")
        return parse(body).also {
            when (it) {
                is LongPollResponse.OutdatedTs -> Log.i(TAG, "long poll ts outdated, new ts=${it.ts}")
                LongPollResponse.KeyExpired -> Log.i(TAG, "long poll key expired")
                is LongPollResponse.Events -> Unit
            }
        }
    }

    private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val outcome = runCatching { response.use { it.code to it.body.string() } }
                    outcome.onSuccess(continuation::resume).onFailure(continuation::resumeWithException)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            },
        )
    }

    companion object {
        const val WAIT_SECONDS = 25
        const val DELETED_FLAG = 128L

        private const val TAG = "LongPoll"

        fun parse(body: String): LongPollResponse {
            val json = Json.parseToJsonElement(body).jsonObject
            json.int("failed")?.let { failed ->
                val ts = json.long("ts")
                return if (failed == 1 && ts != null) LongPollResponse.OutdatedTs(ts) else LongPollResponse.KeyExpired
            }
            val ts = json.long("ts") ?: throw IOException("long poll response without ts")
            val updates = (json["updates"] as? JsonArray).orEmpty().filterIsInstance<JsonArray>()
            return LongPollResponse.Events(updates.mapNotNull(::event), ts)
        }

        private fun event(update: JsonArray): LongPollEvent? {
            fun long(index: Int): Long? = (update.getOrNull(index) as? JsonPrimitive)?.longOrNull
            val first = long(1) ?: return null
            val second = long(2) ?: return null
            return when (long(0)) {
                4L -> long(3)?.let { LongPollEvent.NewMessage(first) }
                2L -> long(3)?.let { peerId -> LongPollEvent.FlagsSet(first, peerId, second) }
                6L -> LongPollEvent.IncomingRead(first, second)
                7L -> LongPollEvent.OutgoingRead(first, second)
                else -> null
            }
        }
    }
}
