package com.drunkbatya.vunder.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface QueueSource {
    val title: String

    @Serializable
    data object MyMusic : QueueSource {
        override val title = "My music"
    }

    @Serializable
    data class Search(val query: String) : QueueSource {
        override val title get() = "Search: $query"
    }

    @Serializable
    data object Saved : QueueSource {
        override val title = "Saved"
    }

    @Serializable
    data object Listened : QueueSource {
        override val title = "Listened"
    }

    @Serializable
    data class FromPlaylist(val playlist: Playlist) : QueueSource {
        override val title get() = playlist.title
    }

    @Serializable
    data object Recommendations : QueueSource {
        override val title = "Recommendations"
    }

    @Serializable
    data class Similar(val seed: Track) : QueueSource {
        override val title get() = "Similar to ${seed.title}"
    }

    @Serializable
    data class Conversation(val peerId: Long, val conversationTitle: String) : QueueSource {
        override val title get() = conversationTitle
    }
}
