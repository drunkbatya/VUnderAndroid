package com.drunkbatya.vunder.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.Track
import com.drunkbatya.vunder.core.music.TrackLibrary
import com.drunkbatya.vunder.ui.components.SearchField
import com.drunkbatya.vunder.ui.components.StatusText
import com.drunkbatya.vunder.ui.track.TrackRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TrackPickerViewModel @Inject constructor(library: TrackLibrary) : ViewModel() {
    val tracks: StateFlow<List<Track>?> = library.tracks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackPickerSheet(
    limit: Int,
    onAttach: (List<Track>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: TrackPickerViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val filterField = rememberTextFieldState()
    val selected = remember { mutableStateListOf<Track>() }
    val query = filterField.text.trim().toString()
    val shown = remember(tracks, query) {
        val lowered = query.lowercase()
        tracks.orEmpty().filter { query.isEmpty() || it.matches(lowered) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.messages_attach_audio), style = MaterialTheme.typography.titleLarge)
                    if (selected.size >= limit) {
                        Text(
                            pluralStringResource(R.plurals.messages_attachment_limit, limit, limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { onAttach(selected.toList()) }, enabled = selected.isNotEmpty()) {
                    Text(
                        if (selected.isEmpty()) {
                            stringResource(R.string.messages_attach)
                        } else {
                            stringResource(R.string.messages_attach_count, selected.size)
                        },
                    )
                }
            }
            SearchField(filterField, stringResource(R.string.messages_filter))
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.storageId }) { track ->
                        val isSelected = selected.any(track::isSame)
                        val enabled = isSelected || selected.size < limit
                        TrackRow(
                            track = track,
                            cover = track.coverUrl,
                            isCurrent = false,
                            isCached = false,
                            modifier = Modifier
                                .clickable(enabled = enabled) { if (isSelected) selected.removeAll(track::isSame) else selected += track }
                                .alpha(if (enabled) 1f else 0.5f),
                        ) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                when {
                    tracks == null -> Unit
                    tracks.isNullOrEmpty() -> StatusText(stringResource(R.string.messages_library_empty))
                    shown.isEmpty() -> StatusText(stringResource(R.string.messages_nothing_matches, query))
                }
            }
        }
    }
}
