package com.drunkbatya.vunder.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.feature.messages.ConversationPickerSheet
import com.drunkbatya.vunder.feature.messages.MessagesTab
import com.drunkbatya.vunder.feature.messages.MessagesViewModel
import com.drunkbatya.vunder.feature.music.MusicTab
import com.drunkbatya.vunder.feature.music.MusicTabViewModel
import com.drunkbatya.vunder.feature.player.MiniPlayer
import com.drunkbatya.vunder.feature.settings.SettingsTab
import com.drunkbatya.vunder.ui.track.TrackActionsViewModel
import com.drunkbatya.vunder.ui.components.MenuItem

enum class MainTab(@StringRes val title: Int, val icon: ImageVector) {
    Music(R.string.tab_music, Icons.Outlined.MusicNote),
    Messages(R.string.tab_messages, Icons.AutoMirrored.Outlined.Chat),
    Settings(R.string.tab_settings, Icons.Outlined.Settings),
}

private sealed interface TrackShare {
    val track: Track

    data class SendFromList(override val track: Track) : TrackShare
    data class AttachInChat(override val track: Track) : TrackShare
}

@Composable
fun MainScreen(
    userId: Long,
    messagesViewModel: MessagesViewModel = hiltViewModel(),
    musicNavigator: MusicTabViewModel = hiltViewModel(),
    trackActions: TrackActionsViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Music) }
    var pendingShare by remember { mutableStateOf<TrackShare?>(null) }
    val unreadMessages by messagesViewModel.unreadTotal.collectAsStateWithLifecycle()
    val tabStates = rememberSaveableStateHolder()

    fun jumpToSource(source: QueueSource, track: Track) {
        if (source is QueueSource.Conversation) {
            selectedTab = MainTab.Messages
            messagesViewModel.revealTrack(source.peerId, source.conversationTitle, track)
        } else {
            selectedTab = MainTab.Music
            musicNavigator.open(source, track)
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer(onJumpToSource = ::jumpToSource, onShare = { pendingShare = TrackShare.AttachInChat(it) })
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tab == MainTab.Messages && unreadMessages > 0) Badge { Text(unreadMessages.toString()) }
                                    },
                                ) {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.title)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            tabStates.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    MainTab.Music -> MusicTab(
                        userId = userId,
                        trackMenuExtras = { track ->
                            if (track.isAvailable) {
                                listOf(MenuItem(R.string.music_send_to_conversation, Icons.AutoMirrored.Rounded.Send) {
                                    pendingShare = TrackShare.SendFromList(track)
                                })
                            } else {
                                emptyList()
                            }
                        },
                    )
                    MainTab.Messages -> MessagesTab(messagesViewModel)
                    MainTab.Settings -> SettingsTab()
                }
            }
        }
    }

    pendingShare?.let { share ->
        ConversationPickerSheet(
            onPick = { peerId, title ->
                pendingShare = null
                when (share) {
                    is TrackShare.SendFromList -> trackActions.send(share.track, peerId, title)
                    is TrackShare.AttachInChat -> {
                        selectedTab = MainTab.Messages
                        messagesViewModel.openChat(peerId, title, attachedTracks = listOf(share.track))
                    }
                }
            },
            onDismiss = { pendingShare = null },
        )
    }
}
