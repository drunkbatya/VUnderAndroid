package com.drunkbatya.vunder.core.audio

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

enum class AudioCodec {
    Mp3,
    Aac;

    companion object {
        private const val FRAME_HEADER_BYTES = 2

        fun of(file: File): AudioCodec = RandomAccessFile(file, "r").use { input ->
            val audioStart = Id3Tag.audioRange(input).first
            val header = ByteArray(FRAME_HEADER_BYTES)
            input.seek(audioStart)
            input.readFully(header)
            of(header) ?: throw IOException("unknown audio frame header %02x %02x at $audioStart in ${file.name}".format(header[0], header[1]))
        }

        fun of(frameHeader: ByteArray): AudioCodec? {
            val first = frameHeader[0].toInt() and 0xFF
            val second = frameHeader[1].toInt() and 0xFF
            if (first != 0xFF) return null
            val layer = (second shr 1) and 0x03
            return when {
                second and 0xF0 == 0xF0 && layer == 0 -> Aac
                second and 0xE0 == 0xE0 && layer == 1 -> Mp3
                else -> null
            }
        }
    }
}
