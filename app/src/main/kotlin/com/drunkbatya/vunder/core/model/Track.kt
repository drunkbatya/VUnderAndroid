package com.drunkbatya.vunder.core.model

import com.drunkbatya.vunder.core.network.long
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.string
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Track(
    val ownerId: Long,
    val id: Long,
    val artist: String,
    val title: String,
    val duration: Int,
    val url: String? = null,
    val accessKey: String? = null,
    val coverUrl: String? = null,
) {
    val key: TrackKey
        get() = TrackKey(ownerId, id)

    val storageId: String
        get() = "${ownerId}_$id"

    val fullId: String
        get() = if (accessKey.isNullOrEmpty()) storageId else "${storageId}_$accessKey"

    val isAvailable: Boolean
        get() = !url.isNullOrEmpty()

    val isHls: Boolean
        get() = url?.contains(".m3u8") == true

    val formattedDuration: String
        get() = formatDuration(duration)

    fun isSame(other: Track?): Boolean = other != null && key == other.key

    fun matches(loweredQuery: String): Boolean =
        artist.lowercase().contains(loweredQuery) || title.lowercase().contains(loweredQuery)

    companion object {
        private const val EXPLICIT_MARKER = " Ⓔ"
        private const val UNAVAILABLE_URL_FRAGMENT = "audio_api_unavailable.mp3"
        private val duplicatedSuffix = Regex(""" \((.+)\) \(\1\)$""")

        fun from(json: JsonObject): Track? {
            val id = json.long("id") ?: json.long("aid") ?: return null
            val ownerId = json.long("owner_id") ?: return null
            val rawUrl = json.string("url").orEmpty()
            val thumb = json.obj("album")?.obj("thumb")
            return Track(
                ownerId = ownerId,
                id = id,
                artist = json.string("artist").orEmpty().removeSuffix(EXPLICIT_MARKER),
                title = cleanTitle(json.string("title").orEmpty().removeSuffix(EXPLICIT_MARKER), json.string("subtitle")),
                duration = json.int("duration") ?: 0,
                url = rawUrl.takeUnless { it.isEmpty() || UNAVAILABLE_URL_FRAGMENT in it },
                accessKey = json.string("access_key")?.takeUnless { it.isEmpty() },
                coverUrl = thumb?.string("photo_300") ?: thumb?.string("photo_270"),
            )
        }

        private fun cleanTitle(title: String, subtitle: String?): String {
            val deduplicated = duplicatedSuffix.replace(title) { " (${it.groupValues[1]})" }
            return if (!subtitle.isNullOrEmpty() && subtitle !in deduplicated) "$deduplicated ($subtitle)" else deduplicated
        }
    }
}

@Serializable
data class TrackKey(val ownerId: Long, val id: Long)

fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
