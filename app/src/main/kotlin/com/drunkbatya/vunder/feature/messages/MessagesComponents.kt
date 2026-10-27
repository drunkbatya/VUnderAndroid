package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.messaging.Conversation
import com.drunkbatya.vunder.core.messaging.OutgoingMessage
import com.drunkbatya.vunder.core.messaging.Profile

@Composable
internal fun Avatar(name: String, photoUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
internal fun ConversationRow(conversation: Conversation, outgoing: OutgoingMessage?, onClick: () -> Unit) {
    val lastMessage = conversation.lastMessage
    val preview = when {
        outgoing?.failure != null -> stringResource(R.string.messages_preview_failed, outgoing.previewText)
        outgoing != null -> stringResource(R.string.messages_preview_sending, outgoing.previewText)
        lastMessage == null -> ""
        lastMessage.isOutgoing -> stringResource(R.string.messages_preview_you, lastMessage.previewText)
        else -> lastMessage.previewText
    }
    ListRow(onClick) {
        Avatar(conversation.title, conversation.photoUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (lastMessage != null) {
                    Text(
                        MessageDates.short(lastMessage.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outgoing?.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unreadCount > 0) UnreadBadge(conversation.unreadCount)
            }
        }
    }
}

@Composable
internal fun ProfileRow(profile: Profile, onClick: () -> Unit) {
    ListRow(onClick, verticalPadding = 6.dp) {
        Avatar(profile.name, profile.photoUrl)
        Text(profile.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ListRow(onClick: () -> Unit, verticalPadding: Dp = 8.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        Modifier
            .height(20.dp)
            .defaultMinSize(minWidth = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase()
