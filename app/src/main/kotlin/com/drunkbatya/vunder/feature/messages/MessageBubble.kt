package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.MessageAttachment
import com.drunkbatya.vunder.core.messaging.VoiceMessage
import com.drunkbatya.vunder.core.model.Track

internal data class BubbleContent(
    val senderName: String?,
    val text: String,
    val attachments: List<MessageAttachment>,
    val isOutgoing: Boolean,
    val footer: String,
    val failed: Boolean,
    val isRead: Boolean?,
)

@Composable
internal fun MessageBubble(
    content: BubbleContent,
    playback: ChatPlayback,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onVoiceClick: (VoiceMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment = if (content.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), contentAlignment = alignment) {
        Box(Modifier.fillMaxWidth(0.8f), contentAlignment = alignment) {
            Column(
                Modifier
                    .widthIn(min = 80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (content.isOutgoing) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content.senderName?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (content.text.isNotEmpty()) {
                    Text(content.text, style = MaterialTheme.typography.bodyLarge)
                }
                content.attachments.forEach { attachment ->
                    when (attachment) {
                        is MessageAttachment.Audio -> AudioAttachment(
                            track = attachment.track,
                            isCurrent = attachment.track.isSame(playback.currentTrack),
                            isPlaying = playback.isMusicPlaying,
                            onClick = { onTrackClick(attachment.track) },
                            onLongClick = { onTrackLongClick(attachment.track) },
                        )
                        is MessageAttachment.Voice -> VoiceAttachment(
                            voice = attachment.voice,
                            isPlaying = playback.playingVoice == attachment.voice,
                            onClick = { onVoiceClick(attachment.voice) },
                        )
                        is MessageAttachment.Other -> Text(
                            stringResource(R.string.messages_other_attachment, attachment.kind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BubbleFooter(content, Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun BubbleFooter(content: BubbleContent, modifier: Modifier) {
    val color = if (content.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(content.footer, style = MaterialTheme.typography.labelSmall, color = color)
        content.isRead?.let { isRead ->
            Icon(if (isRead) Icons.Filled.DoneAll else Icons.Filled.Done, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AudioAttachment(track: Track, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    AttachmentRow(
        icon = if (isCurrent && isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
        enabled = track.isAvailable,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(track.formattedDuration, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VoiceAttachment(voice: VoiceMessage, isPlaying: Boolean, onClick: () -> Unit) {
    AttachmentRow(
        icon = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
        enabled = voice.mp3Url != null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(stringResource(R.string.messages_voice, voice.formattedDuration), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AttachmentRow(
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .then(modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        content()
    }
}

@Composable
internal fun DayHeader(title: String) {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
