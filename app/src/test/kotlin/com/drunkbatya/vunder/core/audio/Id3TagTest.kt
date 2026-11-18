package com.drunkbatya.vunder.core.audio

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Id3TagTest {
    @Test
    fun detectsCodecByFrameHeaderBetweenExistingTags() {
        val leadingTag = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 1, 2) + ByteArray(130) { 0xFF.toByte() }
        val trailingTag = byteArrayOf('T'.code.toByte(), 'A'.code.toByte(), 'G'.code.toByte()) + ByteArray(125)
        val adtsFrames = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()) + ByteArray(200) { 0x5A }
        val mp3Frames = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64) + ByteArray(200) { 0x11 }

        for ((frames, codec) in listOf(adtsFrames to AudioCodec.Aac, mp3Frames to AudioCodec.Mp3)) {
            val file = File.createTempFile("id3", ".audio")
            try {
                file.writeBytes(leadingTag + frames + trailingTag)
                val range = RandomAccessFile(file, "r").use(Id3Tag::audioRange)
                assertEquals(leadingTag.size.toLong() until (leadingTag.size + frames.size).toLong(), range)
                assertEquals(codec, AudioCodec.of(file))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun buildsId3v23WithUtf16TextAndFrontCover() {
        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        val tag = Id3Tag.build(title = "\u0416", artist = "A", cover = cover)

        val title = frame("TIT2", byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte(), 0x16, 0x04))
        val artist = frame("TPE1", byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte(), 'A'.code.toByte(), 0))
        val picture = frame("APIC", byteArrayOf(0) + "image/jpeg".toByteArray() + byteArrayOf(0, 3, 0) + cover)
        val body = title + artist + picture
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, body.size.toByte())
        assertArrayEquals(header + body, tag)
    }

    private fun frame(id: String, content: ByteArray): ByteArray =
        id.toByteArray() + byteArrayOf(0, 0, 0, content.size.toByte(), 0, 0) + content
}
