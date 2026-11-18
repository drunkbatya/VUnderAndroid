package com.drunkbatya.vunder.core.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class Mp3Transcoder @Inject constructor() {
    suspend fun transcode(source: File, destination: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).mime?.startsWith("audio/") == true }
                ?: throw IOException("no audio track in ${source.name}")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.mime ?: throw IOException("no mime type for track $trackIndex in ${source.name}")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            format.setInteger(MediaFormat.KEY_MAX_OUTPUT_CHANNEL_COUNT, MAX_CHANNELS)
            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()
                FileChannel.open(destination.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { output ->
                    transcodeStream(extractor, decoder, output, durationUs, onProgress)
                }
            } finally {
                decoder.release()
            }
            Log.i(TAG, "${source.name} $mime ${source.length()} bytes -> mp3 ${destination.length()} bytes in ${SystemClock.elapsedRealtime() - startedAt} ms")
        } finally {
            extractor.release()
        }
    }

    private suspend fun transcodeStream(extractor: MediaExtractor, decoder: MediaCodec, output: FileChannel, durationUs: Long, onProgress: (Float) -> Unit) {
        val info = MediaCodec.BufferInfo()
        var encoder: Mp3Encoder? = null
        try {
            var isInputDone = false
            var isOutputDone = false
            while (!isOutputDone) {
                currentCoroutineContext().ensureActive()
                if (!isInputDone) isInputDone = feedDecoder(extractor, decoder)
                val index = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                if (index < 0) continue
                if (info.size > 0) {
                    val pcmFormat = decoder.getOutputFormat(index)
                    val activeEncoder = encoder ?: encoderFor(pcmFormat).also { encoder = it }
                    requireSameFormat(activeEncoder, pcmFormat)
                    val samples = decoder.getOutputBuffer(index) ?: throw IOException("decoder output buffer $index is null")
                    samples.limit(info.offset + info.size)
                    samples.position(info.offset)
                    output.write(activeEncoder.encode(samples))
                    if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                }
                decoder.releaseOutputBuffer(index, false)
                isOutputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
            val finishedEncoder = encoder ?: throw IOException("decoder produced no samples")
            output.write(finishedEncoder.flush())
            output.write(finishedEncoder.lameTag(), 0)
            onProgress(1f)
        } finally {
            encoder?.close()
        }
    }

    private fun feedDecoder(extractor: MediaExtractor, decoder: MediaCodec): Boolean {
        val index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (index < 0) return false
        val buffer = decoder.getInputBuffer(index) ?: throw IOException("decoder input buffer $index is null")
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    private fun encoderFor(pcmFormat: MediaFormat): Mp3Encoder {
        val encoding = if (pcmFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
        if (encoding != AudioFormat.ENCODING_PCM_16BIT) throw IOException("decoder pcm encoding $encoding is not 16-bit")
        val channels = pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (channels !in 1..MAX_CHANNELS) throw IOException("decoder produced $channels channels, max $MAX_CHANNELS")
        return Mp3Encoder(pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels)
    }

    private fun requireSameFormat(encoder: Mp3Encoder, pcmFormat: MediaFormat) {
        val sampleRate = pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (sampleRate != encoder.sampleRate || channels != encoder.channels) {
            throw IOException("decoder format changed from ${encoder.sampleRate} Hz x${encoder.channels} to $sampleRate Hz x$channels")
        }
    }

    private val MediaFormat.mime: String?
        get() = getString(MediaFormat.KEY_MIME)

    private companion object {
        const val TAG = "Mp3Transcoder"
        const val MAX_CHANNELS = 2
        const val CODEC_TIMEOUT_US = 10_000L
    }
}
