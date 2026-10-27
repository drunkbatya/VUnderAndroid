package com.drunkbatya.vunder.core.auth

import android.util.Log
import com.drunkbatya.vunder.core.network.ChallengeCoordinator
import com.drunkbatya.vunder.core.network.VkApiException
import com.drunkbatya.vunder.core.session.Session
import com.drunkbatya.vunder.core.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

data class SignInAttempt(val login: String, val anonymousToken: String, val sid: String)

sealed interface AuthStep {
    data class OneTimeCode(val attempt: SignInAttempt, val method: String) : AuthStep {
        val deliversAutomatically: Boolean
            get() = method == "sms" || method == "callreset"

        val canResend: Boolean
            get() = method != "codegen" && method != "reserve_code"
    }

    data class Password(val attempt: SignInAttempt, val grantType: GrantType) : AuthStep

    data object SignedIn : AuthStep
}

@Singleton
class AuthFlow @Inject constructor(
    private val oauth: OAuthClient,
    private val endpoints: AuthEndpoints,
    private val sessionStore: SessionStore,
    private val challenges: ChallengeCoordinator,
) {
    suspend fun start(login: String): AuthStep {
        val trustedHash = sessionStore.trustedHash()
        Log.i(TAG, "sign in started trusted_hash=${trustedHash != null}")
        val anonymousToken = oauth.anonymousToken()
        val account = endpoints.validateAccount(login, anonymousToken, trustedHash)
        Log.i(TAG, "account validated flow=${account.flowName} method=${account.verificationMethod ?: "none"} sid=${account.sid.isNotEmpty()}")
        if (account.sid.isEmpty()) {
            throw AuthException.Failure(
                if (account.flowName == "need_registration") "Account not found: $login" else "Account validation failed: ${account.flowName}",
            )
        }
        val attempt = SignInAttempt(login, anonymousToken, account.sid)
        val method = account.verificationMethod
        return if (!method.isNullOrEmpty() && method != "password") {
            AuthStep.OneTimeCode(attempt, method)
        } else {
            AuthStep.Password(attempt, GrantType.Password)
        }
    }

    suspend fun sendCode(step: AuthStep.OneTimeCode): OneTimeCodeDelivery {
        val attempt = step.attempt
        val method = if (step.deliversAutomatically) {
            step.method
        } else {
            val available = endpoints.verificationMethods(attempt.sid, attempt.anonymousToken)
            Log.i(TAG, "verification methods available: ${available.joinToString(",")}")
            when {
                "sms" in available -> "sms"
                "callreset" in available -> "callreset"
                else -> "sms"
            }
        }
        val delivery = endpoints.sendOneTimeCode(method, attempt.sid, attempt.anonymousToken)
        Log.i(TAG, "one-time code sent via $method to ${delivery.phoneMask ?: "?"} code_length=${delivery.codeLength}")
        return delivery
    }

    suspend fun confirmCode(step: AuthStep.OneTimeCode, code: String): AuthStep {
        val attempt = step.attempt
        val verification = try {
            endpoints.checkOneTimeCode(attempt.sid, code, step.method, attempt.anonymousToken)
        } catch (rejected: VkApiException.Api) {
            Log.w(TAG, "one-time code rejected code=${rejected.error.code} message=${rejected.error.message}")
            throw AuthException.CodeRejected(rejected.error)
        }
        Log.i(TAG, "one-time code accepted can_skip_password=${verification.canSkipPassword}")
        val confirmed = attempt.copy(sid = verification.sid)
        if (!verification.canSkipPassword) return AuthStep.Password(confirmed, GrantType.PhoneConfirmation)
        signIn(TokenGrant(GrantType.WithoutPassword, confirmed, password = ""))
        return AuthStep.SignedIn
    }

    suspend fun submitPassword(step: AuthStep.Password, password: String) {
        signIn(TokenGrant(step.grantType, step.attempt, password))
    }

    private suspend fun signIn(grant: TokenGrant) {
        Log.i(TAG, "requesting token grant=${grant.type.value}")
        val issued = requestToken(grant)
        Log.i(TAG, "token issued user_id=${issued.userId} trusted_hash=${issued.trustedHash != null}")
        issued.trustedHash?.let { sessionStore.saveTrustedHash(it) }
        val exchangeToken = endpoints.exchangeToken(issued.accessToken)
        Log.i(TAG, "exchange token received")
        val userId = endpoints.currentUserId(issued.accessToken)
        sessionStore.save(Session(issued.userId, issued.accessToken, exchangeToken))
        Log.i(TAG, "signed in as user_id=$userId")
    }

    private suspend fun requestToken(initialGrant: TokenGrant): TokenResponse.Issued {
        var grant = initialGrant
        var processingPolls = 0
        while (true) {
            when (val response = oauth.token(grant)) {
                is TokenResponse.Issued -> return response
                TokenResponse.Processing -> {
                    processingPolls++
                    Log.d(TAG, "token endpoint is processing, poll $processingPolls")
                    if (processingPolls > MAX_PROCESSING_POLLS) {
                        throw AuthException.Failure("Token confirmation timed out after $MAX_PROCESSING_POLLS polls")
                    }
                    delay(1.seconds)
                }
                is TokenResponse.Captcha -> {
                    Log.i(TAG, "token endpoint requires captcha sid=${response.sid}")
                    grant = grant.copy(captcha = CaptchaAnswer(response.sid, challenges.solveCaptcha(response.imageUrl)))
                }
                is TokenResponse.Validation -> {
                    Log.i(TAG, "token endpoint requires validation url=${response.url}")
                    val outcome = challenges.completeValidation(response.url)
                    val accessToken = outcome.accessToken
                    val userId = outcome.userId
                    if (accessToken == null || userId == null) {
                        throw AuthException.Failure("Validation finished without access_token or user_id")
                    }
                    return TokenResponse.Issued(accessToken, userId, trustedHash = null)
                }
                is TokenResponse.Failure -> {
                    Log.e(TAG, "token endpoint failed: ${response.message}")
                    throw AuthException.Failure(response.message)
                }
            }
        }
    }

    private companion object {
        const val TAG = "Auth"
        const val MAX_PROCESSING_POLLS = 120
    }
}
