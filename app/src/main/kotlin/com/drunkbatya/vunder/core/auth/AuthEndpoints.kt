package com.drunkbatya.vunder.core.auth

import com.drunkbatya.vunder.core.network.VkApiClient
import com.drunkbatya.vunder.core.network.VkApiException
import com.drunkbatya.vunder.core.network.VkRequest
import com.drunkbatya.vunder.core.network.asObject
import com.drunkbatya.vunder.core.network.bool
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.objects
import com.drunkbatya.vunder.core.network.string
import com.drunkbatya.vunder.core.network.strings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray

data class AccountValidation(val sid: String, val flowName: String, val verificationMethod: String?)

data class OneTimeCodeDelivery(val phoneMask: String?, val codeLength: Int)

data class OneTimeCodeVerification(val sid: String, val canSkipPassword: Boolean)

@Singleton
class AuthEndpoints @Inject constructor(private val api: VkApiClient) {
    suspend fun validateAccount(login: String, anonymousToken: String, trustedHash: String?): AccountValidation {
        val parameters = mutableListOf(
            "login" to login,
            "force_password" to "0",
            "passkey_supported" to "0",
            "supported_ways" to "callreset,codegen,email,reserve_code,password,push,sms",
            "flow_type" to "auth_without_password",
            "sak_version" to "1.112",
        )
        trustedHash?.let { parameters += "accounts_trusted_hashes" to it }
        val response = api.responseObject(VkRequest.auth("auth.validateAccount", parameters, anonymousToken))
        val flowName = response.string("flow_name").orEmpty()
        val nextStep = response.obj("next_step")
        val passwordOffered = flowName == "need_password" &&
            nextStep?.bool("has_another_verification_methods") == true &&
            "password" in response.strings("flow_names")
        return AccountValidation(
            sid = response.string("sid").orEmpty(),
            flowName = flowName,
            verificationMethod = if (passwordOffered) "password" else nextStep?.string("verification_method"),
        )
    }

    suspend fun sendOneTimeCode(method: String, sid: String, anonymousToken: String): OneTimeCodeDelivery {
        val apiMethod = if (method == "callreset") "ecosystem.sendOtpCallReset" else "ecosystem.sendOtpSms"
        val response = api.responseObject(VkRequest.auth(apiMethod, listOf("sid" to sid), anonymousToken))
        return OneTimeCodeDelivery(
            phoneMask = response.string("info"),
            codeLength = if (method == "callreset") response.int("code_length") ?: 6 else 0,
        )
    }

    suspend fun verificationMethods(sid: String, anonymousToken: String): List<String> =
        api.responseObject(VkRequest.auth("ecosystem.getVerificationMethods", listOf("sid" to sid), anonymousToken))
            .objects("methods")
            .mapNotNull { it.string("name") }

    suspend fun checkOneTimeCode(sid: String, code: String, method: String, anonymousToken: String): OneTimeCodeVerification {
        val request = VkRequest.auth(
            "ecosystem.checkOtp",
            listOf("sid" to sid, "code" to code, "verification_method" to method),
            anonymousToken,
        )
        val response = api.responseObject(request)
        val newSid = response.string("sid") ?: throw VkApiException.MalformedResponse(request.method)
        return OneTimeCodeVerification(newSid, response.bool("can_skip_password"))
    }

    suspend fun exchangeToken(accessToken: String): String {
        val request = VkRequest.auth(
            "auth.getExchangeToken",
            listOf("create_common_token" to "1", "create_tier_tokens" to "0"),
            accessToken,
        )
        return api.responseObject(request).objects("users_exchange_tokens").firstOrNull()?.string("common_token")
            ?: throw VkApiException.MalformedResponse(request.method)
    }

    suspend fun currentUserId(accessToken: String): Long {
        val request = VkRequest(
            "users.get",
            listOf("fields" to "photo_100"),
            authorization = VkRequest.Authorization.Token(accessToken),
        )
        return (api.response(request) as? JsonArray)?.firstOrNull()?.asObject?.long("id")
            ?: throw VkApiException.MalformedResponse(request.method)
    }
}
