package com.drunkbatya.vunder.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.feature.messages.MessagesTab
import com.drunkbatya.vunder.feature.music.MusicTab
import com.drunkbatya.vunder.feature.player.MiniPlayer
import com.drunkbatya.vunder.feature.settings.SettingsTab

enum class MainTab(@StringRes val title: Int, val icon: ImageVector) {
    Music(R.string.tab_music, Icons.Outlined.MusicNote),
    Messages(R.string.tab_messages, Icons.AutoMirrored.Outlined.Chat),
    Settings(R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun MainScreen(userId: Long) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Music) }
    Scaffold(
        bottomBar = {
            Column {
                MiniPlayer()
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.title)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                MainTab.Music -> MusicTab(userId)
                MainTab.Messages -> MessagesTab(userId)
                MainTab.Settings -> SettingsTab()
            }
        }
    }
}
