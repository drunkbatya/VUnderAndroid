package com.drunkbatya.vunder.feature.settings

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.BuildConfig
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.settings.Toggle

@Composable
fun SettingsTab(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmingCacheRemoval by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshSavedTracksSize() }
    val current = state ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        ToggleSection(R.string.settings_section_music, MUSIC_TOGGLES, current, viewModel::setToggle)
        ToggleSection(R.string.settings_section_messages, MESSAGES_TOGGLES, current, viewModel::setToggle)
        SettingsSection(R.string.settings_section_cache) {
            ActionRow(
                R.string.settings_saved_tracks_size,
                trailing = {
                    Text(current.savedTracksSize?.let { Formatter.formatFileSize(LocalContext.current, it) } ?: "...")
                },
                onClick = viewModel::refreshSavedTracksSize,
            )
            RowDivider()
            ActionRow(R.string.settings_remove_saved_tracks, color = MaterialTheme.colorScheme.error) { confirmingCacheRemoval = true }
        }
        SettingsSection(R.string.settings_section_account) {
            ActionRow(R.string.settings_sign_out, color = MaterialTheme.colorScheme.error) { confirmingSignOut = true }
        }
        SettingsSection(R.string.settings_section_about) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                trailingContent = {
                    Text(stringResource(R.string.update_version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                },
                colors = rowColors(),
            )
            RowDivider()
            ActionRow(
                R.string.settings_check_for_updates,
                enabled = !current.isCheckingForUpdates,
                trailing = {
                    if (current.isCheckingForUpdates) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                },
                onClick = viewModel::checkForUpdates,
            )
        }
        ToggleSection(R.string.settings_section_debug, DEBUG_TOGGLES, current, viewModel::setToggle)
    }

    if (confirmingCacheRemoval) {
        AlertDialog(
            onDismissRequest = { confirmingCacheRemoval = false },
            title = { Text(stringResource(R.string.settings_remove_saved_tracks_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingCacheRemoval = false
                    viewModel.removeSavedTracks()
                }) {
                    Text(stringResource(R.string.settings_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCacheRemoval = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSignOut = false
                    viewModel.signOut()
                }) {
                    Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

private val MUSIC_TOGGLES = listOf(
    Toggle.History to R.string.settings_history,
    Toggle.Cache to R.string.settings_cache_enabled,
    Toggle.CacheOnlyMine to R.string.settings_cache_only_mine,
)
private val MESSAGES_TOGGLES = listOf(Toggle.MarkAsRead to R.string.settings_mark_as_read)
private val DEBUG_TOGGLES = listOf(Toggle.PreferHls to R.string.settings_prefer_hls, Toggle.LongPoll to R.string.settings_long_poll)

@Composable
private fun SettingsSection(@StringRes title: Int, rows: @Composable () -> Unit) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column { rows() }
    }
}

@Composable
private fun ToggleSection(
    @StringRes title: Int,
    toggles: List<Pair<Toggle, Int>>,
    state: SettingsUiState,
    onToggle: (Toggle, Boolean) -> Unit,
) {
    SettingsSection(title) {
        toggles.forEachIndexed { index, (toggle, label) ->
            if (index > 0) RowDivider()
            val isOn = state.settings[toggle]
            ListItem(
                headlineContent = { Text(stringResource(label)) },
                trailingContent = { Switch(checked = isOn, onCheckedChange = null) },
                colors = rowColors(),
                modifier = Modifier.toggleable(value = isOn, role = Role.Switch) { onToggle(toggle, it) },
            )
        }
    }
}

@Composable
private fun ActionRow(
    @StringRes title: Int,
    color: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title), color = color) },
        trailingContent = trailing,
        colors = rowColors(),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp))

@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)
