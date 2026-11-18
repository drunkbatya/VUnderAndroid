package com.drunkbatya.vunder.core.audio

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

object Id3Tag {
    private const val HEADER_BYTES = 10
    private const val FOOTER_FLAG = 0x10
    private const val V1_TAG_BYTES = 128L
    private const val TEXT_ENCODING_UTF16 = 1
    private const val TEXT_ENCODING_LATIN1 = 0
    private const val PICTURE_TYPE_FRONT_COVER = 3

    fun build(title: String, artist: String, cover: ByteArray?): ByteArray {
        val frames = ByteArrayOutputStream()
        if (title.isNotBlank()) frames.writeFrame("TIT2", textFrame(title))
        if (artist.isNotBlank()) frames.writeFrame("TPE1", textFrame(artist))
        cover?.let(::pictureFrame)?.let { frames.writeFrame("APIC", it) }
        val body = frames.toByteArray()
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + syncsafe(body.size) + body
    }

    fun audioRange(file: RandomAccessFile): LongRange {
        val length = file.length()
        var start = 0L
        if (length >= HEADER_BYTES) {
            val header = ByteArray(HEADER_BYTES)
            file.seek(0)
            file.readFully(header)
            if (header.startsWith("ID3")) {
                val footerBytes = if (header[5].toInt() and FOOTER_FLAG != 0) HEADER_BYTES else 0
                start = minOf(length, HEADER_BYTES + syncsafeValue(header, 6).toLong() + footerBytes)
            }
        }
        var end = length
        if (end - start >= V1_TAG_BYTES) {
            val marker = ByteArray(3)
            file.seek(end - V1_TAG_BYTES)
            file.readFully(marker)
            if (marker.startsWith("TAG")) end -= V1_TAG_BYTES
        }
        return start until end
    }

    private fun textFrame(text: String): ByteArray =
        byteArrayOf(TEXT_ENCODING_UTF16.toByte(), 0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

    private fun pictureFrame(image: ByteArray): ByteArray? {
        val mimeType = when {
            image.size > 2 && image[0] == 0xFF.toByte() && image[1] == 0xD8.toByte() -> "image/jpeg"
            image.size > 4 && image[0] == 0x89.toByte() && image.copyOfRange(1, 4).startsWith("PNG") -> "image/png"
            else -> return null
        }
        return byteArrayOf(TEXT_ENCODING_LATIN1.toByte()) +
            mimeType.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0, PICTURE_TYPE_FRONT_COVER.toByte(), 0) +
            image
    }

    private fun ByteArrayOutputStream.writeFrame(id: String, content: ByteArray) {
        write(id.toByteArray(Charsets.ISO_8859_1))
        write(byteArrayOf((content.size ushr 24).toByte(), (content.size ushr 16).toByte(), (content.size ushr 8).toByte(), content.size.toByte()))
        write(byteArrayOf(0, 0))
        write(content)
    }

    private fun syncsafe(value: Int): ByteArray =
        byteArrayOf((value shr 21 and 0x7F).toByte(), (value shr 14 and 0x7F).toByte(), (value shr 7 and 0x7F).toByte(), (value and 0x7F).toByte())

    private fun syncsafeValue(bytes: ByteArray, offset: Int): Int =
        (0 until 4).fold(0) { value, index -> (value shl 7) or (bytes[offset + index].toInt() and 0x7F) }

    private fun ByteArray.startsWith(marker: String): Boolean =
        size >= marker.length && marker.indices.all { this[it] == marker[it].code.toByte() }
}
