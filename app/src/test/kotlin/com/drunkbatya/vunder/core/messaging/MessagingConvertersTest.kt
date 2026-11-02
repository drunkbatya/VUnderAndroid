package com.drunkbatya.vunder.core.messaging

import org.junit.Assert.assertEquals
import org.junit.Test

class MessagingConvertersTest {
    @Test
    fun storesLastMessageWithPhotoAttachment() {
        val converters = MessagingConverters()
        val message = Message(
            id = 10,
            peerId = 42,
            fromId = 42,
            date = 1730000000,
            text = "",
            isOutgoing = false,
            attachments = listOf(MessageAttachment.Other("photo")),
            randomId = 0,
        )

        assertEquals(message, converters.messageFromJson(converters.messageToJson(message)))
    }
}
