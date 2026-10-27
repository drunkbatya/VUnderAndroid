package com.drunkbatya.vunder.core.messaging

import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed interface MessageAttachment {
    val previewText: String

    @Serializable
    @SerialName("audio")
    data class Audio(val track: Track) : MessageAttachment {
        override val previewText get() = "Audio: ${track.artist} - ${track.title}"
    }

    @Serializable
    @SerialName("voice")
    data class Voice(val voice: VoiceMessage) : MessageAttachment {
        override val previewText get() = "Voice message ${voice.formattedDuration}"
    }

    @Serializable
    @SerialName("other")
    data class Other(val kind: String) : MessageAttachment {
        override val previewText get() = "Attachment: $kind"
    }

    companion object {
        fun from(json: JsonObject): MessageAttachment? = when (val type = json.string("type")) {
            null -> null
            "audio" -> json.obj("audio")?.let(Track::from)?.let(::Audio)
            "audio_message" -> json.obj("audio_message")?.let(VoiceMessage::from)?.let(::Voice)
            else -> Other(type)
        }
    }
}
