package com.drunkbatya.vunder.core.music

import android.util.Log
import com.drunkbatya.vunder.core.model.Playlist
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.network.VkApiClient
import com.drunkbatya.vunder.core.network.VkApiException
import com.drunkbatya.vunder.core.network.VkRequest
import com.drunkbatya.vunder.core.network.asObject
import com.drunkbatya.vunder.core.network.int
import com.drunkbatya.vunder.core.network.obj
import com.drunkbatya.vunder.core.network.objects
import com.drunkbatya.vunder.core.settings.SettingsRepository
import com.drunkbatya.vunder.core.settings.Toggle
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

data class TrackPage(val tracks: List<Track>, val total: Int)

data class Recommendations(val tracks: List<Track>, val personal: Boolean)

@Singleton
class AudioApi @Inject constructor(
    private val apiClient: VkApiClient,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun tracksPage(ownerId: Long, playlist: Playlist? = null, offset: Int, count: Int = LIBRARY_PAGE_SIZE): TrackPage {
        val parameters = mutableListOf(
            "owner_id" to ownerId.toString(),
            "offset" to offset.toString(),
            "count" to count.toString(),
        )
        if (playlist != null) {
            parameters += "album_id" to playlist.id.toString()
            playlist.accessKey?.let { parameters += "access_key" to it }
        }
        val response = audioResponse("audio.get", parameters)
        return TrackPage(response.tracks(), response.int("count") ?: 0)
    }

    suspend fun allTracks(ownerId: Long, playlist: Playlist? = null, onProgress: (loaded: Int, total: Int) -> Unit): List<Track> {
        val tracks = mutableListOf<Track>()
        do {
            val page = tracksPage(ownerId, playlist, offset = tracks.size)
            tracks += page.tracks
            Log.i(TAG, "tracks page loaded owner=$ownerId playlist=${playlist?.id ?: 0} offset=${tracks.size - page.tracks.size} items=${page.tracks.size} total=${page.total}")
            onProgress(tracks.size, page.total)
        } while (page.tracks.isNotEmpty() && tracks.size < page.total)
        return tracks
    }

    suspend fun search(query: String, offset: Int, count: Int = SEARCH_PAGE_SIZE): List<Track> {
        val tracks = audioResponse(
            "audio.search",
            listOf("q" to query, "offset" to offset.toString(), "count" to count.toString()),
        ).tracks()
        Log.i(TAG, "search '$query' offset=$offset items=${tracks.size}")
        return tracks
    }

    suspend fun recommendations(userId: Long): Recommendations {
        val personal = audioResponse(
            "audio.getRecommendations",
            listOf("user_id" to userId.toString(), "count" to "200"),
        ).tracks()
        Log.i(TAG, "recommendations loaded user=$userId items=${personal.size}")
        if (personal.isNotEmpty()) return Recommendations(personal, personal = true)
        val general = audioResponse(
            "audio.get",
            listOf("owner_id" to userId.toString(), "playlist_id" to "-21", "count" to "2000"),
        ).tracks()
        Log.i(TAG, "recommendations fallback playlist -21 items=${general.size}")
        return Recommendations(general, personal = false)
    }

    suspend fun similar(track: Track): Recommendations {
        val code = buildString {
            append("var a=")
            if (!track.isAvailable) append("API.audio.search({q:\"${track.searchQuery}\",count:3}).items+")
            append("API.audio.getRecommendations({target_audio:\"${track.fullId}\",count:200}).items;")
            append("if(a)return{items:a};")
            append("return{shuffle:1,items:API.audio.search({q:\"${track.artistQuery}\",count:50}).items};")
        }
        val response = audioResponse("execute", listOf("code" to code))
        val personal = !response.containsKey("shuffle")
        val tracks = response.tracks().let { if (personal) it else it.shuffled() }
        Log.i(TAG, "similar to ${track.fullId} items=${tracks.size} fallback=${!personal}")
        return Recommendations(tracks, personal)
    }

    suspend fun add(track: Track): Long {
        val parameters = mutableListOf("owner_id" to track.ownerId.toString(), "audio_id" to track.id.toString())
        track.accessKey?.let { parameters += "access_key" to it }
        val newId = (audioCall("audio.add", parameters)["response"] as? JsonPrimitive)?.longOrNull
        if (newId == null || newId <= 0) throw VkApiException.MalformedResponse("audio.add")
        Log.i(TAG, "added ${track.fullId} as $newId")
        return newId
    }

    suspend fun restore(track: Track) {
        audioCall("audio.restore", track.ownerAndAudioId())
        Log.i(TAG, "restored ${track.storageId}")
    }

    suspend fun delete(track: Track) {
        audioCall("audio.delete", track.ownerAndAudioId())
        Log.i(TAG, "deleted ${track.storageId}")
    }

    suspend fun freshUrl(track: Track): String? {
        val items = audioCall("audio.getById", listOf("audios" to track.fullId))["response"] as? JsonArray
        val url = items?.firstOrNull()?.asObject?.let(Track::from)?.url
        Log.i(TAG, "fresh url for ${track.fullId}: ${url != null}")
        return url
    }

    suspend fun playlists(ownerId: Long): List<Playlist> {
        val playlists = audioResponse(
            "audio.getPlaylists",
            listOf("owner_id" to ownerId.toString(), "filters" to "all", "extended" to "1", "count" to "200"),
        ).objects("items").mapNotNull(Playlist::from)
        Log.i(TAG, "playlists loaded owner=$ownerId items=${playlists.size}")
        return playlists
    }

    private suspend fun audioCall(method: String, parameters: List<Pair<String, String>>): JsonObject {
        if (settingsRepository.current()[Toggle.PreferHls]) {
            return apiClient.call(VkRequest.audio(method, parameters, VkRequest.AUDIO_HLS_VERSION))
        }
        return try {
            apiClient.call(VkRequest.audio(method, parameters))
        } catch (original: VkApiException.Api) {
            if (original.error.code in NON_VERSION_ERROR_CODES) throw original
            Log.e(TAG, "$method v=${VkRequest.AUDIO_VERSION} failed ${original.error.code}: ${original.error.message}, retrying with v=${VkRequest.AUDIO_HLS_VERSION}")
            val json = try {
                apiClient.call(VkRequest.audio(method, parameters, VkRequest.AUDIO_HLS_VERSION))
            } catch (_: VkApiException) {
                throw original
            } catch (_: IOException) {
                throw original
            }
            settingsRepository.set(Toggle.PreferHls, true)
            Log.e(TAG, "audio api v=${VkRequest.AUDIO_VERSION} failed for $method, preferHls=true")
            json
        }
    }

    private suspend fun audioResponse(method: String, parameters: List<Pair<String, String>>): JsonObject =
        audioCall(method, parameters).obj("response") ?: throw VkApiException.MalformedResponse(method)

    private fun JsonObject.tracks(): List<Track> = objects("items").mapNotNull(Track::from)

    private fun Track.ownerAndAudioId() = listOf("owner_id" to ownerId.toString(), "audio_id" to id.toString())

    companion object {
        const val LIBRARY_PAGE_SIZE = 1000
        const val SEARCH_PAGE_SIZE = 200
        private const val TAG = "AudioApi"
        private val NON_VERSION_ERROR_CODES = setOf(5, 6, 9, 10, 14, 17, 24, 29)
    }
}
