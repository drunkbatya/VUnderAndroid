package com.drunkbatya.vunder.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ValidationOutcome(
    val accessToken: String?,
    val userId: Long?,
)

sealed interface Challenge {
    data class Captcha(val imageUrl: String, internal val answer: CompletableDeferred<String?>) : Challenge
    data class Validation(val url: String, internal val answer: CompletableDeferred<ValidationOutcome?>) : Challenge
}

@Singleton
class ChallengeCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<Challenge?>(null)
    val pending: StateFlow<Challenge?> = _pending.asStateFlow()

    suspend fun solveCaptcha(imageUrl: String): String =
        present(Challenge.Captcha(imageUrl, CompletableDeferred())) { it.answer.await() }

    suspend fun completeValidation(url: String): ValidationOutcome =
        present(Challenge.Validation(url, CompletableDeferred())) { it.answer.await() }

    fun submitCaptcha(challenge: Challenge.Captcha, key: String?) = resolve(challenge) { challenge.answer.complete(key) }

    fun finishValidation(challenge: Challenge.Validation, outcome: ValidationOutcome?) =
        resolve(challenge) { challenge.answer.complete(outcome) }

    private suspend fun <C : Challenge, R : Any> present(challenge: C, await: suspend (C) -> R?): R = mutex.withLock {
        _pending.value = challenge
        try {
            await(challenge) ?: throw VkApiException.ChallengeCancelled()
        } finally {
            _pending.compareAndSet(challenge, null)
        }
    }

    private fun resolve(challenge: Challenge, complete: () -> Unit) {
        _pending.compareAndSet(challenge, null)
        complete()
    }
}
