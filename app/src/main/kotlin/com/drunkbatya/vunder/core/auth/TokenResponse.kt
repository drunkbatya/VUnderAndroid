package com.drunkbatya.vunder.core.auth

import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.json.JsonObject

sealed interface TokenResponse {
    data class Issued(val accessToken: String, val userId: Long, val trustedHash: String?) : TokenResponse
    data object Processing : TokenResponse
    data class Captcha(val sid: String, val imageUrl: String) : TokenResponse
    data class Validation(val url: String) : TokenResponse
    data class Failure(val message: String) : TokenResponse

    companion object {
        fun from(json: JsonObject): TokenResponse {
            if ("processing" in json) return Processing
            val accessToken = json.string("access_token")
            val userId = json.long("user_id")
            if (accessToken != null && userId != null) return Issued(accessToken, userId, json.string("trusted_hash"))
            if ("error" !in json) return Failure("unexpected token response")

            val errorObject = json.obj("error")
            val errorName = json.string("error").orEmpty()
            val details = errorObject ?: json
            val code = errorObject?.int("error_code") ?: 0
            val captchaSid = details.string("captcha_sid")
            val captchaImage = details.string("captcha_img")
            if ((code == 14 || errorName == "need_captcha") && captchaSid != null && captchaImage != null) {
                return Captcha(captchaSid, captchaImage)
            }
            val redirectUrl = details.string("redirect_uri")
            if ((code == 17 || errorName == "need_validation") && redirectUrl != null) return Validation(redirectUrl)

            val description = json.string("error_description")
                ?: errorObject?.string("error_msg")
                ?: details.obj("ban_info")?.string("message")
            return Failure(
                when {
                    description != null -> "${errorName.ifEmpty { "error" }}: $description"
                    errorName.isEmpty() -> "error $code"
                    else -> errorName
                },
            )
        }
    }
}
