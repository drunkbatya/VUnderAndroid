package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.ui.components.RoundedTextField

@Composable
internal fun ChatInputBar(
    draft: TextFieldState,
    attachments: List<Track>,
    onAttach: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column {
            HorizontalDivider()
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                attachments.forEachIndexed { index, track ->
                    AttachmentChip(track) { onRemoveAttachment(index) }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = onAttach, enabled = attachments.size < ChatViewModel.MAX_ATTACHMENTS) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.messages_attach_audio))
                    }
                    RoundedTextField(
                        state = draft,
                        placeholder = stringResource(R.string.messages_input_hint),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                    )
                    IconButton(onClick = onSend, enabled = draft.text.isNotBlank() || attachments.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.messages_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(track: Track, onRemove: () -> Unit) {
    Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            "${track.artist} - ${track.title}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = stringResource(R.string.messages_remove_attachment),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
