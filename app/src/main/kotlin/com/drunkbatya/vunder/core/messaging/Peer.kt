package com.drunkbatya.vunder.core.messaging

object Peer {
    const val CHAT_BASE = 2_000_000_000L

    fun isChat(peerId: Long): Boolean = peerId >= CHAT_BASE

    fun isGroup(peerId: Long): Boolean = peerId < 0
}
