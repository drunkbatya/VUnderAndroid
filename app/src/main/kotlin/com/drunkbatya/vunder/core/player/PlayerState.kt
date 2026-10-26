package com.drunkbatya.vunder.core.player

import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.settings.RepeatMode
import kotlin.time.Duration

enum class PlaybackStatus {
    Idle,
    Loading,
    Playing,
    Paused,
}

data class PlaybackProgress(
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
)

data class PlayerState(
    val orderedQueue: List<Track> = emptyList(),
    val currentOrderPosition: Int? = null,
    val source: QueueSource? = null,
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
) {
    val current: Track?
        get() = currentOrderPosition?.let(orderedQueue::getOrNull)

    val upcoming: Track?
        get() = currentOrderPosition?.let { orderedQueue.getOrNull(it + 1) }

    val remainingCount: Int
        get() = currentOrderPosition?.let { orderedQueue.size - it - 1 } ?: 0

    val isPlaying: Boolean
        get() = status == PlaybackStatus.Playing || status == PlaybackStatus.Loading
}
