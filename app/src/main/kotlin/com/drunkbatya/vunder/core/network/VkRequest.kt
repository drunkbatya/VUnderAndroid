package com.drunkbatya.vunder.core.network

data class VkRequest(
    val method: String,
    val parameters: List<Pair<String, String>> = emptyList(),
    val version: String = DEFAULT_VERSION,
    val agent: VkClientIdentity.AppVersion = VkClientIdentity.AppVersion.General,
    val authorization: Authorization = Authorization.Session,
    val includesDeviceId: Boolean = false,
) {
    sealed interface Authorization {
        data object Session : Authorization
        data class Token(val token: String) : Authorization
        data object None : Authorization
    }

    val usesBearerHeader: Boolean
        get() = version == AUTH_VERSION

    companion object {
        const val DEFAULT_VERSION = "5.81"
        const val AUTH_VERSION = "5.272"
        const val AUDIO_VERSION = "5.87"
        const val AUDIO_HLS_VERSION = "5.92"

        fun auth(method: String, parameters: List<Pair<String, String>>, token: String? = null) = VkRequest(
            method = method,
            parameters = parameters + ("api_id" to VkClientIdentity.CLIENT_ID),
            version = AUTH_VERSION,
            agent = VkClientIdentity.AppVersion.Auth,
            authorization = token?.let(Authorization::Token) ?: Authorization.Session,
            includesDeviceId = true,
        )

        fun audio(method: String, parameters: List<Pair<String, String>>, version: String = AUDIO_VERSION) = VkRequest(
            method = method,
            parameters = parameters,
            version = version,
            agent = VkClientIdentity.AppVersion.Audio,
        )
    }
}
