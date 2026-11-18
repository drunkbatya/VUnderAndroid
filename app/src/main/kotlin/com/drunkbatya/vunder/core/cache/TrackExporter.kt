package com.drunkbatya.vunder.core.cache

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.drunkbatya.vunder.core.audio.Id3Tag
import com.drunkbatya.vunder.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TrackExporter @Inject constructor(@ApplicationContext context: Context) {
    private val contentResolver = context.contentResolver

    suspend fun placeInMusicFolder(source: File, track: Track, cover: ByteArray?) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${fileName(track)}.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$MUSIC_SUBDIRECTORY")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values) ?: throw IOException("MediaStore insert failed for ${track.storageId}")
        try {
            val output = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("cannot open $uri for writing")
            output.use { stream ->
                stream.write(Id3Tag.build(track.title, track.artist, cover))
                copyAudio(source, stream)
            }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (failure: Exception) {
            contentResolver.delete(uri, null, null)
            throw failure
        }
        Log.i(TAG, "exported ${track.storageId} to $uri")
    }

    private fun copyAudio(source: File, output: OutputStream) = RandomAccessFile(source, "r").use { input ->
        val audio = Id3Tag.audioRange(input)
        input.seek(audio.first)
        val chunk = ByteArray(CHUNK_BYTES)
        var remaining = audio.last - audio.first + 1
        while (remaining > 0) {
            val count = input.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("${source.name} ended with $remaining bytes left")
            output.write(chunk, 0, count)
            remaining -= count
        }
    }

    suspend fun isInMusicFolder(track: Track): Boolean = withContext(Dispatchers.IO) {
        contentResolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
            arrayOf("${Environment.DIRECTORY_MUSIC}/$MUSIC_SUBDIRECTORY/", "${fileName(track)}.mp3"),
            null,
        )?.use { it.count > 0 } ?: false
    }

    companion object {
        private const val MIME_TYPE = "audio/mpeg"
        private const val TAG = "TrackExporter"
        private const val MUSIC_SUBDIRECTORY = "VUnder"
        private const val INVALID_CHARACTERS = "/\\:?*\"<>|"
        private const val MAX_NAME_LENGTH = 120
        private const val CHUNK_BYTES = 65_536

        fun fileName(track: Track): String {
            val name = "${track.artist} - ${track.title}"
                .map { if (it in INVALID_CHARACTERS || it.isISOControl()) '_' else it }
                .joinToString("")
                .trim()
            return name.ifEmpty { track.storageId }.take(MAX_NAME_LENGTH)
        }
    }
}
