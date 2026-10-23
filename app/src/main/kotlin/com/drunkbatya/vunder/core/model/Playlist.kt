package com.drunkbatya.vunder.core.model

import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Playlist(
    val ownerId: Long,
    val id: Long,
    val title: String,
    val accessKey: String?,
    val trackCount: Int,
    val coverUrl: String?,
) {
    companion object {
        fun from(json: JsonObject): Playlist? {
            val id = json.long("id") ?: return null
            val ownerId = json.long("owner_id") ?: return null
            return Playlist(
                ownerId = ownerId,
                id = id,
                title = json.string("title").orEmpty(),
                accessKey = json.string("access_key")?.takeUnless { it.isEmpty() },
                trackCount = json.int("count") ?: 0,
                coverUrl = json.obj("photo")?.string("photo_135"),
            )
        }
    }
}
