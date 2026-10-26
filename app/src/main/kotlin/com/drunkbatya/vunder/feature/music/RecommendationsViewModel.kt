package com.drunkbatya.vunder.feature.music

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.model.QueueSource
import com.drunkbatya.vunder.core.music.AudioApi
import com.drunkbatya.vunder.core.network.NetworkMonitor
import com.drunkbatya.vunder.ui.track.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = RecommendationsViewModel.Factory::class)
class RecommendationsViewModel @AssistedInject constructor(
    @Assisted source: QueueSource,
    @Assisted private val userId: Long,
    private val audioApi: AudioApi,
    private val network: NetworkMonitor,
    private val holder: RecommendationsHolder,
) : FilterableTrackListViewModel(source) {
    init {
        val stored = if (source == QueueSource.Recommendations) holder.tracks(userId) else null
        if (stored != null) tracks.value = stored else load()
    }

    override fun emptyMessage(loaded: Boolean): UiText = when {
        !network.isConnected -> UiText.Resource(R.string.music_no_internet)
        loaded -> UiText.Resource(R.string.music_nothing_found)
        else -> UiText.Resource(R.string.music_pull_to_load)
    }

    fun load() {
        if (loading.value) return
        if (!network.isConnected) {
            if (tracks.value != null) notify(UiText.Resource(R.string.music_no_internet))
            return
        }
        loading.value = true
        viewModelScope.launch {
            try {
                val seed = (source as? QueueSource.Similar)?.seed
                val recommendations = if (seed != null) audioApi.similar(seed) else audioApi.recommendations(userId)
                tracks.value = recommendations.tracks
                if (seed == null) holder.store(userId, recommendations.tracks)
                if (!recommendations.personal && recommendations.tracks.isNotEmpty()) {
                    notify(
                        if (seed != null) {
                            UiText.Resource(R.string.music_similar_fallback, listOf(seed.artist))
                        } else {
                            UiText.Resource(R.string.music_recommendations_fallback)
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "${source.title} failed: ${failure.message}")
                notify(UiText.failure(failure))
            } finally {
                loading.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(source: QueueSource, userId: Long): RecommendationsViewModel
    }

    private companion object {
        const val TAG = "Recommendations"
    }
}
