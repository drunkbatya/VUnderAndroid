package com.drunkbatya.vunder.feature.music

import com.drunkbatya.vunder.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationsHolder @Inject constructor() {
    private var userId: Long? = null
    private var tracks: List<Track>? = null

    fun tracks(userId: Long): List<Track>? = tracks.takeIf { this.userId == userId }

    fun store(userId: Long, tracks: List<Track>) {
        this.userId = userId
        this.tracks = tracks
    }
}
