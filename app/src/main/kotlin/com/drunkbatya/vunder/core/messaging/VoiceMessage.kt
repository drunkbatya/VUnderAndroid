package com.drunkbatya.vunder.core.messaging

import com.drunkbatya.vunder.core.model.formatDuration
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class VoiceMessage(
    val id: Long,
    val ownerId: Long,
    val duration: Int,
    val mp3Url: String?,
) {
    val storageId: String
        get() = "${ownerId}_$id"

    val formattedDuration: String
        get() = formatDuration(duration)

    companion object {
        fun from(json: JsonObject): VoiceMessage? {
            val id = json.long("id") ?: return null
            val ownerId = json.long("owner_id") ?: return null
            return VoiceMessage(
                id = id,
                ownerId = ownerId,
                duration = json.int("duration") ?: 0,
                mp3Url = json.string("link_mp3") ?: json.string("link_ogg"),
            )
        }
    }
}
