package com.drunkbatya.vunder.core.music

import com.drunkbatya.vunder.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackUrlRefresher @Inject constructor(
    private val audioApi: AudioApi,
    private val library: TrackLibrary,
) {
    suspend fun freshUrl(track: Track): String? {
        val url = audioApi.freshUrl(track) ?: return null
        library.updateUrl(track, url)
        return url
    }

    suspend fun storedUrl(track: Track): String? = library.storedUrl(track) ?: track.url
}
