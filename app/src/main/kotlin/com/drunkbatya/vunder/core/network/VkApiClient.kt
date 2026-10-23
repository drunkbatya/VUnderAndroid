package com.drunkbatya.vunder.core.network

import android.util.Log
import com.drunkbatya.vunder.core.session.SessionStore
import java.security.MessageDigest
import java.util.HexFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class VkApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val challengeHandler: ChallengeHandler,
) {
    private val refreshMutex = Mutex()

    suspend fun call(request: VkRequest): JsonObject = call(request, captcha = null, retriedAfterRefresh = false)

    suspend fun response(request: VkRequest): JsonElement =
        call(request)["response"] ?: throw VkApiException.MalformedResponse(request.method)

    suspend fun responseObject(request: VkRequest): JsonObject =
        response(request).asObject ?: throw VkApiException.MalformedResponse(request.method)

    private suspend fun call(request: VkRequest, captcha: Captcha?, retriedAfterRefresh: Boolean): JsonObject {
        val session = sessionStore.current()
        val parameters = mutableListOf(
            "v" to request.version,
            "lang" to VkClientIdentity.language,
            "https" to "1",
        )
        parameters += request.parameters
        if (request.includesDeviceId) parameters += "device_id" to sessionStore.deviceId()
        captcha?.let {
            parameters += "captcha_sid" to it.sid
            parameters += "captcha_key" to it.key
        }

        var accessToken: String? = null
        var signingSecret: String? = null
        when (val authorization = request.authorization) {
            VkRequest.Authorization.Session -> if (session != null) {
                accessToken = session.accessToken
                signingSecret = session.exchangeToken
            } else {
                parameters += "api_id" to VkClientIdentity.CLIENT_ID
            }
            is VkRequest.Authorization.Token -> accessToken = authorization.token
            VkRequest.Authorization.None -> Unit
        }
        if (accessToken != null && parameters.none { it.first == "access_token" }) {
            parameters += "access_token" to accessToken
        }
        if (accessToken != null && signingSecret != null) {
            parameters += "sig" to signature(request.method, parameters, signingSecret)
        }

        val bearer = if (request.usesBearerHeader) parameters.firstOrNull { it.first == "access_token" }?.second else null
        val bodyParameters = if (bearer == null) parameters else parameters.filter { it.first != "access_token" }

        val httpRequest = Request.Builder()
            .url("https://${VkClientIdentity.API_HOST}/method/${request.method}")
            .header("User-Agent", VkClientIdentity.userAgent(request.agent))
            .header("Accept", "application/json")
            .header("x-vk-android-client", "new")
            .header("x-screen", "nowhere")
            .apply { bearer?.let { header("Authorization", "Bearer $it") } }
            .post(FormBody.Builder().apply { bodyParameters.forEach { (key, value) -> add(key, value) } }.build())
            .build()

        Log.d(TAG, "-> ${request.method} ${LogRedaction.parameters(bodyParameters)} bearer=${bearer != null}")
        val startedAt = TimeSource.Monotonic.markNow()
        val (status, body) = withContext(Dispatchers.IO) {
            httpClient.newCall(httpRequest).execute().use { it.code to it.body.string() }
        }
        val elapsed = startedAt.elapsedNow().inWholeMilliseconds
        val json = runCatching { Json.parseToJsonElement(body).asObject }.getOrNull() ?: run {
            Log.e(TAG, "<- ${request.method} http=$status ${elapsed}ms malformed body ${body.length} chars")
            throw VkApiException.MalformedResponse(request.method)
        }
        val errorJson = json.obj("error") ?: run {
            Log.d(TAG, "<- ${request.method} http=$status ${elapsed}ms ${body.length} chars")
            return json
        }
        val error = VkApiError.from(errorJson)
        Log.e(TAG, "<- ${request.method} http=$status ${elapsed}ms error ${error.code}: ${error.message}")

        if (error.isExpiredToken && request.authorization == VkRequest.Authorization.Session && session != null && !retriedAfterRefresh) {
            Log.i(TAG, "access token expired, refreshing before retrying ${request.method}")
            refreshAccessToken(expired = session.accessToken)
            return call(request, captcha, retriedAfterRefresh = true)
        }
        if (error.code == 14 && error.captchaSid != null && error.captchaImageUrl != null) {
            Log.i(TAG, "captcha required for ${request.method} sid=${error.captchaSid}")
            val key = challengeHandler.solveCaptcha(error.captchaImageUrl)
            return call(request, Captcha(error.captchaSid, key), retriedAfterRefresh)
        }
        if (error.code == 17 && error.redirectUrl != null) {
            Log.i(TAG, "validation required for ${request.method} url=${error.redirectUrl}")
            challengeHandler.completeValidation(error.redirectUrl)
            return call(request, captcha, retriedAfterRefresh)
        }
        throw VkApiException.Api(error)
    }

    private suspend fun refreshAccessToken(expired: String) = refreshMutex.withLock {
        val session = sessionStore.current() ?: throw VkApiException.SessionExpired()
        if (session.accessToken != expired) {
            Log.i(TAG, "access token already refreshed elsewhere")
            return@withLock
        }
        val token = try {
            requestRefreshedToken(session.exchangeToken)
        } catch (rejected: VkApiException.Api) {
            Log.e(TAG, "token refresh rejected ${rejected.error.code}: ${rejected.error.message}, clearing session")
            sessionStore.clear()
            throw VkApiException.SessionExpired()
        }
        sessionStore.updateAccessToken(token)
        Log.i(TAG, "access token refreshed")
    }

    private suspend fun requestRefreshedToken(exchangeToken: String): String {
        val request = VkRequest.auth(
            "auth.refreshTokens",
            listOf(
                "client_id" to VkClientIdentity.CLIENT_ID,
                "client_secret" to VkClientIdentity.CLIENT_SECRET,
                "exchange_tokens" to exchangeToken,
                "active_index" to "0",
                "scope" to "all",
                "initiator" to "expired_token",
            ),
        ).copy(authorization = VkRequest.Authorization.None)
        val json = call(request, captcha = null, retriedAfterRefresh = true)
        val success = json.obj("response")?.objects("success")?.firstOrNull()
        val token = success?.obj("access_token")?.string("token")
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "token refresh response without token: ${LogRedaction.json(json)}")
            throw VkApiException.MalformedResponse(request.method)
        }
        Log.i(TAG, "token refresh expires_in=${success.obj("access_token")?.int("expires_in") ?: 0}")
        return token
    }

    private data class Captcha(val sid: String, val key: String)

    companion object {
        private const val TAG = "VkApi"

        fun signature(method: String, parameters: List<Pair<String, String>>, secret: String): String {
            val query = parameters.joinToString("&") { (key, value) -> "$key=$value" }
            val digest = MessageDigest.getInstance("MD5").digest("/method/$method?$query$secret".encodeToByteArray())
            return HexFormat.of().formatHex(digest)
        }
    }
}
