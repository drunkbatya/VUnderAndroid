package com.drunkbatya.vunder.core.hls

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MpegTsDemuxerTest {
    @Test
    fun extractsAudioElementaryStreamAcrossSegmentBoundaries() {
        val firstFrame = ByteArray(100) { it.toByte() }
        val continuation = ByteArray(184) { (200 + it).toByte() }
        val secondFrame = ByteArray(50) { (it * 3).toByte() }
        val stream = packet(0x0000, payloadUnitStart = true, programAssociation()) +
            packet(0x1000, payloadUnitStart = true, programMap()) +
            adaptationPacket(0x0101, pes(firstFrame)) +
            packet(0x0102, payloadUnitStart = true, pes(ByteArray(20))) +
            packet(0x0101, payloadUnitStart = false, continuation) +
            adaptationPacket(0x0101, pes(secondFrame))

        val demuxer = MpegTsDemuxer()
        val split = 188 * 2 + 77
        val audio = demuxer.demux(stream.copyOfRange(0, split)) + demuxer.demux(stream.copyOfRange(split, stream.size))

        assertEquals(0x03, demuxer.audioStreamType)
        assertArrayEquals(firstFrame + continuation + secondFrame, audio)
    }

    private fun programAssociation(): ByteArray = section(
        byteArrayOf(0x00, 0xB0.toByte(), 13, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, 0x00, 0x01, 0xF0.toByte(), 0x00) + crc(),
    )

    private fun programMap(): ByteArray = section(
        byteArrayOf(
            0x02, 0xB0.toByte(), 23, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00, 0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00,
            0x1B, 0xE1.toByte(), 0x02, 0xF0.toByte(), 0x00,
            0x03, 0xE1.toByte(), 0x01, 0xF0.toByte(), 0x00,
        ) + crc(),
    )

    private fun section(table: ByteArray): ByteArray = byteArrayOf(0x00) + table

    private fun crc(): ByteArray = ByteArray(4)

    private fun pes(frame: ByteArray): ByteArray =
        byteArrayOf(0x00, 0x00, 0x01, 0xC0.toByte(), 0x00, 0x00, 0x80.toByte(), 0x80.toByte(), 0x05, 1, 2, 3, 4, 5) + frame

    private fun packet(pid: Int, payloadUnitStart: Boolean, payload: ByteArray): ByteArray {
        val header = byteArrayOf(
            0x47,
            ((if (payloadUnitStart) 0x40 else 0x00) or (pid shr 8)).toByte(),
            (pid and 0xFF).toByte(),
            0x10,
        )
        return header + payload + ByteArray(184 - payload.size) { 0xFF.toByte() }
    }

    private fun adaptationPacket(pid: Int, payload: ByteArray): ByteArray {
        val adaptationLength = 183 - payload.size
        val header = byteArrayOf(0x47, (0x40 or (pid shr 8)).toByte(), (pid and 0xFF).toByte(), 0x30)
        return header + byteArrayOf(adaptationLength.toByte()) + ByteArray(adaptationLength) + payload
    }
}
