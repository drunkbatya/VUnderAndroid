package com.drunkbatya.vunder.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

fun JsonObject.bool(key: String): Boolean {
    val primitive = get(key) as? JsonPrimitive ?: return false
    return primitive.booleanOrNull ?: primitive.intOrNull?.let { it != 0 } ?: (primitive.contentOrNull == "1")
}

fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

fun JsonObject.objects(key: String): List<JsonObject> = (get(key) as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

fun JsonObject.strings(key: String): List<String> =
    (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

val JsonElement.asObject: JsonObject?
    get() = this as? JsonObject
