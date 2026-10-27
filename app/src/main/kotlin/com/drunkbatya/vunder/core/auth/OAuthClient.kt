package com.drunkbatya.vunder.core.auth

import android.util.Log
import com.drunkbatya.vunder.core.network.LogRedaction
import com.drunkbatya.vunder.core.network.VkClientIdentity
import com.drunkbatya.vunder.core.network.VkRequest
import com.drunkbatya.vunder.core.network.asObject
import com.drunkbatya.vunder.core.network.string
import com.drunkbatya.vunder.core.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request

enum class GrantType(val value: String) {
    Password("password"),
    WithoutPassword("without_password"),
    PhoneConfirmation("phone_confirmation_sid"),
}

data class CaptchaAnswer(val sid: String, val key: String)

data class TokenGrant(
    val type: GrantType,
    val attempt: SignInAttempt,
    val password: String,
    val captcha: CaptchaAnswer? = null,
)

@Singleton
class OAuthClient @Inject constructor(
    httpClient: OkHttpClient,
    private val sessionStore: SessionStore,
) {
    private val httpClient = httpClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()

    suspend fun anonymousToken(): String {
        val json = post(
            "get_anonym_token",
            listOf(
                "client_secret" to VkClientIdentity.CLIENT_SECRET,
                "client_id" to VkClientIdentity.CLIENT_ID,
                "v" to VkRequest.AUTH_VERSION,
                "lang" to VkClientIdentity.language,
                "https" to "1",
                "app_id" to VkClientIdentity.CLIENT_ID,
                "device_id" to sessionStore.deviceId(),
            ),
        )
        return json.string("token")
            ?: throw AuthException.Failure(json.string("error_description") ?: json.string("error") ?: "anonymous token unavailable")
    }

    suspend fun token(grant: TokenGrant): TokenResponse {
        val parameters = mutableListOf(
            "grant_type" to grant.type.value,
            "scope" to "all",
            "client_id" to VkClientIdentity.CLIENT_ID,
            "client_secret" to VkClientIdentity.CLIENT_SECRET,
            "username" to grant.attempt.login,
            "password" to if (grant.type == GrantType.WithoutPassword) "" else grant.password,
            "2fa_supported" to "1",
            "libverify_support" to "1",
            "device_trusted_hash_support" to "1",
            "supported_ways" to "push,email",
            "flow_type" to "tg_flow",
            "sak_version" to "1.142",
            "sid" to grant.attempt.sid,
            "anonymous_token" to grant.attempt.anonymousToken,
            "v" to VkRequest.AUTH_VERSION,
            "https" to "1",
            "api_id" to VkClientIdentity.CLIENT_ID,
            "lang" to VkClientIdentity.language,
            "device_id" to sessionStore.deviceId(),
        )
        grant.captcha?.let {
            parameters += "captcha_sid" to it.sid
            parameters += "captcha_key" to it.key
        }
        return TokenResponse.from(post("token", parameters))
    }

    private suspend fun post(path: String, parameters: List<Pair<String, String>>): JsonObject {
        val request = Request.Builder()
            .url("https://${VkClientIdentity.OAUTH_HOST}/$path")
            .header("User-Agent", VkClientIdentity.userAgent(VkClientIdentity.AppVersion.Auth))
            .header("x-vk-android-client", "new")
            .header("x-screen", "nowhere")
            .post(FormBody.Builder().apply { parameters.forEach { (key, value) -> add(key, value) } }.build())
            .build()
        Log.d(TAG, "-> oauth/$path ${LogRedaction.parameters(parameters)}")
        val (status, body) = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { it.code to it.body.string() }
        }
        val json = runCatching { Json.parseToJsonElement(body).asObject }.getOrNull() ?: run {
            Log.e(TAG, "<- oauth/$path http=$status malformed body ${body.length} chars")
            throw AuthException.Failure("malformed oauth response for $path")
        }
        Log.d(TAG, "<- oauth/$path http=$status ${LogRedaction.json(json)}")
        return json
    }

    private companion object {
        const val TAG = "OAuth"
    }
}
