package com.drunkbatya.vunder.core.session

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val userId: Long,
    val accessToken: String,
    val exchangeToken: String,
)
