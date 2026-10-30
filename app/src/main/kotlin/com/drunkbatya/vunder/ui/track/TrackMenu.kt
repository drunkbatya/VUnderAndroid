package com.drunkbatya.vunder.ui.track

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.LibraryMembership
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.ui.components.MenuItem
import com.drunkbatya.vunder.ui.components.MenuSheet

@Composable
fun TrackMenuSheet(track: Track, cover: Any?, items: List<MenuItem>, onDismiss: () -> Unit) {
    MenuSheet(items, onDismiss) {
        ListItem(
            leadingContent = { CoverImage(cover, size = 48.dp) },
            headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

fun TrackActionsViewModel.commonMenuItems(track: Track): List<MenuItem> = buildList {
    val decorations = decorations.value
    add(MenuItem(R.string.track_play_next, Icons.AutoMirrored.Rounded.PlaylistPlay) { playNext(track) })
    add(MenuItem(R.string.track_add_to_queue, Icons.AutoMirrored.Rounded.PlaylistAdd) { addToQueue(track) })
    if (track.key in decorations.cachedKeys) {
        add(MenuItem(R.string.track_remove_offline, Icons.Rounded.Delete, destructive = true) { removeOffline(track) })
    } else if (track.isAvailable) {
        add(MenuItem(R.string.download_kind_cache, Icons.Rounded.DownloadForOffline) { saveOffline(track) })
    }
    if (track.isAvailable && decorations.library.membership(track) != LibraryMembership.Mine) {
        add(MenuItem(R.string.track_add_to_library, Icons.Rounded.AddCircleOutline) { addToLibrary(track) })
    }
}

@Composable
fun DeleteConfirmationDialog(track: Track, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        text = stringResource(R.string.track_delete_confirm, track.artist, track.title),
        confirmTitle = stringResource(R.string.track_delete),
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun DownloadConfirmationDialog(track: Track, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        text = stringResource(R.string.track_download_confirm, track.artist, track.title),
        confirmTitle = stringResource(R.string.track_download),
        confirmColor = MaterialTheme.colorScheme.primary,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ConfirmationDialog(text: String, confirmTitle: String, confirmColor: Color, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(confirmTitle, color = confirmColor)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.track_cancel)) } },
    )
}
