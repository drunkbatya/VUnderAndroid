package com.drunkbatya.vunder.core.network

interface ChallengeHandler {
    suspend fun solveCaptcha(imageUrl: String): String
    suspend fun completeValidation(url: String): ValidationOutcome
}

data class ValidationOutcome(
    val accessToken: String?,
    val secret: String?,
    val userId: Long?,
)
