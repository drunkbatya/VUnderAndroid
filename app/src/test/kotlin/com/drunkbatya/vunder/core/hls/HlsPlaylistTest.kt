package com.drunkbatya.vunder.core.hls

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsPlaylistTest {
    private val baseUrl = "https://cs1.vkuseraudio.net/s/v1/ac/abc/index.m3u8?siren=1".toHttpUrl()

    @Test
    fun masterPlaylistResolvesVariantsAgainstBase() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=128000,CODECS="mp4a.40.2,mp3"
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=320000
            /high/index.m3u8
            """.trimIndent(),
            baseUrl,
        ) as HlsPlaylist.Master

        assertEquals(
            listOf(
                HlsVariant("https://cs1.vkuseraudio.net/s/v1/ac/abc/low/index.m3u8".toHttpUrl(), 128000),
                HlsVariant("https://cs1.vkuseraudio.net/high/index.m3u8".toHttpUrl(), 320000),
            ),
            playlist.variants,
        )
    }

    @Test
    fun mediaPlaylistCarriesKeyUntilMethodNone() {
        val playlist = HlsPlaylist.parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-KEY:METHOD=AES-128,URI="key.pub",IV=0x000102030405060708090a0b0c0d0e0f
            #EXTINF:10.0,
            seg-1.ts
            #EXTINF:10.0,
            seg-2.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4.5,
            seg-3.ts
            #EXT-X-ENDLIST
            """.trimIndent(),
            baseUrl,
        ) as HlsPlaylist.Media

        assertEquals(listOf("seg-1.ts", "seg-2.ts", "seg-3.ts"), playlist.segments.map { it.url.pathSegments.last() })
        val key = playlist.segments[1].key
        assertEquals("https://cs1.vkuseraudio.net/s/v1/ac/abc/key.pub".toHttpUrl(), key.url)
        assertArrayEquals(ByteArray(16) { it.toByte() }, key.iv)
        assertNull(playlist.segments[2].key.url)
    }

    @Test(expected = HlsException::class)
    fun unsupportedKeyMethodFails() {
        HlsPlaylist.parse("#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"k\"\n#EXTINF:1,\na.ts", baseUrl)
    }
}
