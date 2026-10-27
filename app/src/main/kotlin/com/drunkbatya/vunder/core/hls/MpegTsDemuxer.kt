package com.drunkbatya.vunder.core.hls

import java.io.ByteArrayOutputStream

class MpegTsDemuxer {
    private var programMapPid: Int? = null
    private var audioPid: Int? = null
    private var pendingPacket = ByteArray(0)

    var audioStreamType: Int? = null
        private set

    fun demux(segment: ByteArray): ByteArray {
        val input = pendingPacket + segment
        val output = ByteArrayOutputStream()
        var offset = 0
        while (offset + PACKET_SIZE <= input.size) {
            if (input[offset] != SYNC_BYTE) {
                offset++
                continue
            }
            handlePacket(input.copyOfRange(offset, offset + PACKET_SIZE), output)
            offset += PACKET_SIZE
        }
        pendingPacket = input.copyOfRange(offset, input.size)
        return output.toByteArray()
    }

    private fun handlePacket(packet: ByteArray, output: ByteArrayOutputStream) {
        val payloadUnitStart = (packet.u8(1) and 0x40) != 0
        val pid = ((packet.u8(1) and 0x1F) shl 8) or packet.u8(2)
        val adaptationControl = (packet.u8(3) shr 4) and 0x03
        if (adaptationControl == 0x02) return
        val payloadOffset = if (adaptationControl == 0x03) 5 + packet.u8(4) else 4
        if (payloadOffset >= packet.size) return
        val payload = packet.copyOfRange(payloadOffset, packet.size)

        when (pid) {
            0 -> if (payloadUnitStart) section(payload)?.let { programMapPid = programMapPidOf(it) }
            programMapPid -> if (payloadUnitStart) section(payload)?.let { table ->
                val audio = audioStreamOf(table)
                audioPid = audio?.first
                audioStreamType = audio?.second
            }
            audioPid -> if (!payloadUnitStart) {
                output.write(payload)
            } else if (payload.size >= 9 && payload.u8(0) == 0 && payload.u8(1) == 0 && payload.u8(2) == 1) {
                val start = 9 + payload.u8(8)
                if (start <= payload.size) output.write(payload, start, payload.size - start)
            }
        }
    }

    private fun section(payload: ByteArray): ByteArray? {
        if (payload.isEmpty()) return null
        val start = 1 + payload.u8(0)
        if (start + 3 > payload.size) return null
        val sectionLength = ((payload.u8(start + 1) and 0x0F) shl 8) or payload.u8(start + 2)
        return payload.copyOfRange(start, minOf(start + 3 + sectionLength, payload.size))
    }

    private fun programMapPidOf(table: ByteArray): Int? {
        if (table.u8(0) != 0x00 || table.size <= 12) return null
        var offset = 8
        while (offset + 4 <= table.size - 4) {
            val programNumber = (table.u8(offset) shl 8) or table.u8(offset + 1)
            val pid = ((table.u8(offset + 2) and 0x1F) shl 8) or table.u8(offset + 3)
            if (programNumber != 0) return pid
            offset += 4
        }
        return null
    }

    private fun audioStreamOf(table: ByteArray): Pair<Int, Int>? {
        if (table.u8(0) != 0x02 || table.size <= 16) return null
        val programInfoLength = ((table.u8(10) and 0x0F) shl 8) or table.u8(11)
        var offset = 12 + programInfoLength
        while (offset + 5 <= table.size - 4) {
            val streamType = table.u8(offset)
            val pid = ((table.u8(offset + 1) and 0x1F) shl 8) or table.u8(offset + 2)
            val infoLength = ((table.u8(offset + 3) and 0x0F) shl 8) or table.u8(offset + 4)
            if (streamType in AUDIO_STREAM_TYPES) return pid to streamType
            offset += 5 + infoLength
        }
        return null
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private companion object {
        const val PACKET_SIZE = 188
        const val SYNC_BYTE: Byte = 0x47
        val AUDIO_STREAM_TYPES = setOf(0x03, 0x04, 0x0F)
    }
}
