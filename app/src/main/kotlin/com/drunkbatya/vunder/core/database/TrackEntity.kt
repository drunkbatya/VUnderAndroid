package com.drunkbatya.vunder.core.database

import androidx.room.Entity
import androidx.room.Index
import com.drunkbatya.vunder.core.model.Track

@Entity(
    tableName = "track",
    primaryKeys = ["ownerId", "id"],
    indices = [Index("libraryPosition")],
)
data class TrackEntity(
    val ownerId: Long,
    val id: Long,
    val artist: String,
    val title: String,
    val duration: Int,
    val url: String?,
    val accessKey: String?,
    val coverUrl: String?,
    val libraryPosition: Int?,
    val cachedAt: Long?,
) {
    fun toTrack() = Track(ownerId, id, artist, title, duration, url, accessKey, coverUrl)
}

fun Track.toEntity(libraryPosition: Int? = null, cachedAt: Long? = null) =
    TrackEntity(ownerId, id, artist, title, duration, url, accessKey, coverUrl, libraryPosition, cachedAt)
