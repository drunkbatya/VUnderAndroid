package com.drunkbatya.vunder.core.audio

import java.io.IOException
import java.nio.ByteBuffer

class Mp3Encoder(val sampleRate: Int, val channels: Int) : AutoCloseable {
    private val frameBytes = channels * Short.SIZE_BYTES
    private var handle = LameNative.create(sampleRate, channels)
    private var pcm = ByteBuffer.allocateDirect(INITIAL_PCM_BYTES)
    private var mp3 = ByteBuffer.allocateDirect(mp3Capacity(INITIAL_PCM_BYTES / frameBytes))

    init {
        if (handle == 0L) throw IOException("lame init failed: rate=$sampleRate channels=$channels")
    }

    fun encode(samples: ByteBuffer): ByteBuffer {
        val byteCount = samples.remaining()
        if (byteCount % frameBytes != 0) throw IOException("pcm chunk of $byteCount bytes is not a multiple of $frameBytes")
        val frameCount = byteCount / frameBytes
        if (pcm.capacity() < byteCount) pcm = ByteBuffer.allocateDirect(byteCount)
        if (mp3.capacity() < mp3Capacity(frameCount)) mp3 = ByteBuffer.allocateDirect(mp3Capacity(frameCount))
        pcm.clear()
        pcm.put(samples)
        return mp3Frames("encode", LameNative.encode(handle, pcm, frameCount, mp3))
    }

    fun flush(): ByteBuffer = mp3Frames("flush", LameNative.flush(handle, mp3))

    fun lameTag(): ByteBuffer = mp3Frames("lame tag", LameNative.lameTag(handle, mp3))

    override fun close() {
        if (handle == 0L) return
        LameNative.close(handle)
        handle = 0L
    }

    private fun mp3Frames(operation: String, result: Int): ByteBuffer {
        if (result < 0) throw IOException("lame $operation failed: code=$result")
        mp3.clear()
        mp3.limit(result)
        return mp3
    }

    private companion object {
        const val INITIAL_PCM_BYTES = 16_384
        const val MP3_BUFFER_MARGIN_BYTES = 7_200

        fun mp3Capacity(frameCount: Int): Int = frameCount + frameCount / 4 + MP3_BUFFER_MARGIN_BYTES
    }
}

private object LameNative {
    init {
        System.loadLibrary("lame_native")
    }

    @JvmStatic external fun create(sampleRate: Int, channels: Int): Long

    @JvmStatic external fun encode(handle: Long, pcm: ByteBuffer, frameCount: Int, mp3: ByteBuffer): Int

    @JvmStatic external fun flush(handle: Long, mp3: ByteBuffer): Int

    @JvmStatic external fun lameTag(handle: Long, mp3: ByteBuffer): Int

    @JvmStatic external fun close(handle: Long)
}
