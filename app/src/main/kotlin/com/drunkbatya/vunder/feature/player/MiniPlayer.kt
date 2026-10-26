package com.drunkbatya.vunder.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.player.PlaybackProgress
import com.drunkbatya.vunder.ui.track.CoverImage
import com.drunkbatya.vunder.ui.track.TrackActionsViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    onJumpToSource: (QueueSource, Track) -> Unit,
    onShare: (Track) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    trackActions: TrackActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    val track = state.current
    LaunchedEffect(track == null) {
        if (track == null) {
            playerOpen = false
            queueOpen = false
        }
    }
    if (track == null) return

    MiniPlayerBar(
        track = track,
        cover = trackActions.cover(track),
        isPlaying = state.isPlaying,
        progress = viewModel.progress,
        onOpen = { playerOpen = true },
        onPlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
    )
    if (playerOpen) {
        PlayerSheet(
            viewModel = viewModel,
            trackActions = trackActions,
            onOpenQueue = { queueOpen = true },
            onJumpToSource = { source, current ->
                playerOpen = false
                onJumpToSource(source, current)
            },
            onShare = { current ->
                playerOpen = false
                onShare(current)
            },
            onDismiss = { playerOpen = false },
        )
    }
    if (queueOpen) {
        QueueSheet(viewModel = viewModel, trackActions = trackActions, onDismiss = { queueOpen = false })
    }
}

@Composable
private fun MiniPlayerBar(
    track: Track,
    cover: Any?,
    isPlaying: Boolean,
    progress: StateFlow<PlaybackProgress>,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.clickable(onClick = onOpen)) {
            MiniPlayerProgress(progress)
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CoverImage(cover, size = 40.dp, modifier = Modifier.padding(end = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous))
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(progress: StateFlow<PlaybackProgress>) {
    val current by progress.collectAsStateWithLifecycle()
    LinearProgressIndicator(
        progress = { current.fraction },
        modifier = Modifier.fillMaxWidth().height(2.dp),
        trackColor = Color.Transparent,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

internal val PlaybackProgress.fraction: Float
    get() = if (duration.isPositive()) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
