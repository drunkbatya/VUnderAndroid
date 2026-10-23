package com.drunkbatya.vunder.core.network

import kotlinx.serialization.json.JsonObject

data class VkApiError(
    val code: Int,
    val message: String,
    val captchaSid: String?,
    val captchaImageUrl: String?,
    val redirectUrl: String?,
) {
    val isExpiredToken: Boolean
        get() = (code == 5 && "expired" in message) || code == 1117

    val isWrongCode: Boolean
        get() = (code and (1 shl 30).inv()) == 8

    companion object {
        fun from(json: JsonObject) = VkApiError(
            code = json.int("error_code") ?: -1,
            message = json.string("error_msg") ?: json.string("error_text").orEmpty(),
            captchaSid = json.string("captcha_sid"),
            captchaImageUrl = json.string("captcha_img"),
            redirectUrl = json.string("redirect_uri"),
        )
    }
}

sealed class VkApiException(message: String) : Exception(message) {
    class Api(val error: VkApiError) : VkApiException("${error.message} (code ${error.code})")
    class MalformedResponse(method: String) : VkApiException("Malformed response for $method")
    class SessionExpired : VkApiException("Session expired")
    class ChallengeCancelled : VkApiException("Verification cancelled")
}

class OfflineException : Exception("No internet connection")
