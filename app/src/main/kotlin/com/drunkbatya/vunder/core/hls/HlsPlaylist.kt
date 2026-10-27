package com.drunkbatya.vunder.core.hls

import java.io.IOException
import okhttp3.HttpUrl

class HlsException(message: String) : IOException(message)

data class HlsVariant(val url: HttpUrl, val bandwidth: Int)

class HlsKey(val url: HttpUrl?, val iv: ByteArray?)

class HlsSegment(val url: HttpUrl, val key: HlsKey)

sealed interface HlsPlaylist {
    data class Master(val variants: List<HlsVariant>) : HlsPlaylist

    data class Media(val segments: List<HlsSegment>) : HlsPlaylist

    companion object {
        fun parse(text: String, baseUrl: HttpUrl): HlsPlaylist {
            val variants = mutableListOf<HlsVariant>()
            val segments = mutableListOf<HlsSegment>()
            var key = HlsKey(url = null, iv = null)
            var pendingBandwidth: Int? = null
            var pendingSegment = false
            for (line in text.lines().map(String::trim).filter(String::isNotEmpty)) {
                when {
                    line.startsWith("#EXT-X-STREAM-INF:") -> pendingBandwidth = attributes(line)["BANDWIDTH"]?.toIntOrNull() ?: 0
                    line.startsWith("#EXT-X-KEY:") -> key = key(attributes(line), baseUrl)
                    line.startsWith("#EXTINF:") -> pendingSegment = true
                    line.startsWith("#") -> Unit
                    else -> {
                        val url = baseUrl.resolve(line) ?: continue
                        val bandwidth = pendingBandwidth
                        if (bandwidth != null) {
                            variants += HlsVariant(url, bandwidth)
                            pendingBandwidth = null
                        } else if (pendingSegment) {
                            segments += HlsSegment(url, key)
                            pendingSegment = false
                        }
                    }
                }
            }
            return when {
                variants.isNotEmpty() -> Master(variants)
                segments.isNotEmpty() -> Media(segments)
                else -> throw HlsException("HLS playlist has no entries: $baseUrl")
            }
        }

        private fun key(attributes: Map<String, String>, baseUrl: HttpUrl): HlsKey {
            val method = attributes["METHOD"] ?: "NONE"
            if (method == "NONE") return HlsKey(url = null, iv = null)
            val url = attributes["URI"]?.let(baseUrl::resolve)
            if (method != "AES-128" || url == null) throw HlsException("HLS key method $method is not supported")
            return HlsKey(url, attributes["IV"]?.let(::hexBytes))
        }

        private fun attributes(line: String): Map<String, String> {
            val colon = line.indexOf(':')
            if (colon < 0) return emptyMap()
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            for (character in line.substring(colon + 1)) {
                if (character == '"') {
                    inQuotes = !inQuotes
                } else if (character == ',' && !inQuotes) {
                    parts += current.toString()
                    current.clear()
                    continue
                }
                current.append(character)
            }
            parts += current.toString()
            return parts.mapNotNull { part ->
                val equals = part.indexOf('=')
                if (equals < 0) null else part.substring(0, equals) to part.substring(equals + 1).replace("\"", "")
            }.toMap()
        }

        private fun hexBytes(text: String): ByteArray? {
            val hex = text.lowercase().removePrefix("0x")
            if (hex.length % 2 != 0) return null
            return ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
            }
        }
    }
}
