package com.drunkbatya.vunder.feature.music

import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.music.TrackLibrary
import com.drunkbatya.vunder.ui.track.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = StoredTracksViewModel.Factory::class)
class StoredTracksViewModel @AssistedInject constructor(
    @Assisted source: QueueSource,
    library: TrackLibrary,
) : FilterableTrackListViewModel(source) {
    init {
        viewModelScope.launch {
            (if (source == QueueSource.Saved) library.saved else library.listened).collect { tracks.value = it }
        }
    }

    override fun emptyMessage(loaded: Boolean): UiText? = when {
        !loaded -> null
        source == QueueSource.Saved -> UiText.Resource(R.string.music_no_saved)
        else -> UiText.Resource(R.string.music_no_listened)
    }

    @AssistedFactory
    interface Factory {
        fun create(source: QueueSource): StoredTracksViewModel
    }
}
