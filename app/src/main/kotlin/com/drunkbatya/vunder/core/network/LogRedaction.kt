package com.drunkbatya.vunder.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object LogRedaction {
    private val secretKeyFragments = listOf("token", "secret", "hash", "password", "sig")
    private val hiddenKeys = setOf("password", "sig", "client_secret")

    fun parameters(parameters: List<Pair<String, String>>): String =
        parameters.joinToString("&") { (key, value) -> "$key=${value(value, key)}" }

    fun json(json: JsonObject): String = json.keys.sorted().joinToString(" ") { key ->
        val rendered = when (val element = json.getValue(key)) {
            is JsonPrimitive -> value(element.content, key)
            is JsonArray -> "[...]"
            is JsonObject -> "{${json(element)}}"
        }
        "$key=$rendered"
    }

    fun value(value: String, key: String): String = when {
        key in hiddenKeys -> "***"
        secretKeyFragments.none { it in key } -> value
        value.length <= 12 -> "***"
        else -> "${value.take(4)}...${value.takeLast(4)}"
    }
}
