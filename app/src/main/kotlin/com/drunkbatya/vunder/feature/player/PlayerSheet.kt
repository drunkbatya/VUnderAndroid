package com.drunkbatya.vunder.feature.player

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.LibraryMembership
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.model.formatDuration
import com.drunkbatya.vunder.core.player.PlaybackProgress
import com.drunkbatya.vunder.core.player.PlayerState
import com.drunkbatya.vunder.core.settings.RepeatMode
import com.drunkbatya.vunder.ui.track.CoverImage
import com.drunkbatya.vunder.ui.track.DeleteConfirmationDialog
import com.drunkbatya.vunder.ui.track.DownloadConfirmationDialog
import com.drunkbatya.vunder.ui.track.NoticeToasts
import com.drunkbatya.vunder.ui.track.TrackActionsViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheet(
    viewModel: PlayerViewModel,
    trackActions: TrackActionsViewModel,
    onOpenQueue: () -> Unit,
    onJumpToSource: (QueueSource, Track) -> Unit,
    onShare: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val encoding by viewModel.encoding.collectAsStateWithLifecycle()
    val decorations by trackActions.decorations.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val track = state.current ?: return
    var deleting by remember { mutableStateOf<Track?>(null) }
    var downloading by remember { mutableStateOf<Track?>(null) }
    val context = LocalContext.current
    NoticeToasts(trackActions.notices)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        PlayerContent(
            track = track,
            cover = trackActions.cover(track),
            state = state,
            progress = progress,
            encoding = encoding?.formatted(context),
            membership = decorations.library.membership(track),
            onJumpToSource = { source -> onJumpToSource(source, track) },
            onSeek = viewModel::seek,
            onPlayPause = viewModel::togglePlayPause,
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
            onToggleShuffle = viewModel::toggleShuffle,
            onCycleRepeat = viewModel::cycleRepeatMode,
            onOpenQueue = onOpenQueue,
            onAddToLibrary = { trackActions.addToLibrary(track) },
            onDeleteFromLibrary = { deleting = track },
            onShare = { onShare(track) },
            canDownload = canDownload,
            onDownload = { downloading = track },
        )
    }

    deleting?.let { pending ->
        DeleteConfirmationDialog(pending, onConfirm = { trackActions.deleteFromLibrary(pending) }, onDismiss = { deleting = null })
    }
    downloading?.let { pending ->
        DownloadConfirmationDialog(pending, onConfirm = { trackActions.downloadToMusicFolder(pending) }, onDismiss = { downloading = null })
    }
}

@Composable
private fun PlayerContent(
    track: Track,
    cover: Any?,
    state: PlayerState,
    progress: PlaybackProgress,
    encoding: String?,
    membership: LibraryMembership,
    onJumpToSource: (QueueSource) -> Unit,
    onSeek: (Duration) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToLibrary: () -> Unit,
    onDeleteFromLibrary: () -> Unit,
    onShare: () -> Unit,
    canDownload: Boolean,
    onDownload: () -> Unit,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    var seekingSeconds by remember { mutableStateOf<Float?>(null) }
    val durationSeconds = progress.duration.inWholeSeconds.toFloat()
    val positionSeconds = seekingSeconds ?: progress.position.inWholeSeconds.toFloat()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val source = state.source
        TextButton(onClick = { source?.let(onJumpToSource) }, enabled = source != null) {
            Text(source?.title ?: stringResource(R.string.queue_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (source != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
        Spacer(Modifier.height(8.dp))
        CoverImage(cover, size = 320.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(24.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = secondary, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            encoding ?: " ",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Slider(
            value = positionSeconds.coerceIn(0f, durationSeconds.coerceAtLeast(1f)),
            onValueChange = { seekingSeconds = it },
            onValueChangeFinished = {
                seekingSeconds?.let { onSeek(it.toDouble().seconds) }
                seekingSeconds = null
            },
            valueRange = 0f..durationSeconds.coerceAtLeast(1f),
        )
        Row(Modifier.fillMaxWidth()) {
            val timeStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
            Text(formatDuration(positionSeconds.toInt()), style = timeStyle, color = secondary, modifier = Modifier.weight(1f))
            Text(
                "-" + formatDuration((durationSeconds - positionSeconds).toInt().coerceAtLeast(0)),
                style = timeStyle,
                color = secondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(40.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ModeButton(Icons.Rounded.Shuffle, R.string.player_shuffle, if (state.shuffle) accent else secondary, onToggleShuffle)
            ModeButton(
                if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                R.string.player_repeat,
                if (state.repeatMode == RepeatMode.Off) secondary else accent,
                onCycleRepeat,
            )
            ModeButton(Icons.AutoMirrored.Rounded.QueueMusic, R.string.queue_title, secondary, onOpenQueue)
            when {
                membership == LibraryMembership.Mine ->
                    ModeButton(Icons.Rounded.RemoveCircleOutline, R.string.track_delete_from_library, secondary, onDeleteFromLibrary)
                track.isAvailable ->
                    ModeButton(Icons.Rounded.AddCircleOutline, R.string.track_add_to_library, secondary, onAddToLibrary)
            }
            if (track.isAvailable) {
                ModeButton(Icons.Rounded.Download, R.string.download_kind_music_folder, secondary, onDownload, enabled = canDownload)
                ModeButton(Icons.AutoMirrored.Rounded.Send, R.string.player_share, secondary, onShare)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModeButton(icon: ImageVector, @StringRes description: Int, tint: Color, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = stringResource(description), tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}
