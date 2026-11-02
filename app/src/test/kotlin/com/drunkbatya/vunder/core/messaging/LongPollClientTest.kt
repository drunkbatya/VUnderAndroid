package com.drunkbatya.vunder.core.messaging

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPollClientTest {
    @Test
    fun parsesMode2UpdatesAndSkipsUnknownOrIncompleteOnes() {
        val body = """
            {"ts":1730000005,"updates":[
                [4,501,35,2000000007,1730000000,"hello",{"from":"42"},{}],
                [2,498,128,123],
                [2,499,128],
                [6,123,497],
                [7,2000000007,500],
                [8,-42,1,1730000000],
                [4,"bad",1,2]
            ]}
        """.trimIndent()

        assertEquals(
            LongPollResponse.Events(
                listOf(
                    LongPollEvent.NewMessage(501),
                    LongPollEvent.FlagsSet(id = 498, peerId = 123, mask = 128),
                    LongPollEvent.IncomingRead(peerId = 123, messageId = 497),
                    LongPollEvent.OutgoingRead(peerId = 2000000007, messageId = 500),
                ),
                ts = 1730000005,
            ),
            LongPollClient.parse(body),
        )
    }

    @Test
    fun mapsFailedCodesToTsRefreshOrNewServer() {
        assertEquals(LongPollResponse.OutdatedTs(77), LongPollClient.parse("""{"failed":1,"ts":77}"""))
        assertEquals(LongPollResponse.KeyExpired, LongPollClient.parse("""{"failed":2}"""))
        assertEquals(LongPollResponse.KeyExpired, LongPollClient.parse("""{"failed":3}"""))
    }
}
