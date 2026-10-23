package com.drunkbatya.vunder.core.network

import java.security.SecureRandom
import java.util.HexFormat
import java.util.Locale

object VkClientIdentity {
    const val CLIENT_ID = "2274003"
    const val CLIENT_SECRET = "hHbZxrka2uZ6jB1inYsH"
    const val API_HOST = "api.vk.ru"
    const val OAUTH_HOST = "oauth.vk.ru"

    enum class AppVersion(val code: String) {
        General("8.108-26257"),
        Auth("8.183-54468"),
        Audio("8.96-22758"),
    }

    fun userAgent(version: AppVersion): String =
        "VKAndroidApp/${version.code} (Android 11; SDK 30; arm64-v8a; Xiaomi; M2101K6G; $language; 2400x1080)"

    val language: String
        get() {
            val preferred = Locale.getDefault().language.let { if (it == "uk") "ua" else it }
            return if (preferred in setOf("en", "ru", "ua", "be")) preferred else "en"
        }

    fun makeDeviceId(): String {
        val random = SecureRandom()
        val hex = HexFormat.of()
        return "${hex.toHexDigits(random.nextLong())}:${hex.formatHex(ByteArray(16).also(random::nextBytes))}"
    }
}
