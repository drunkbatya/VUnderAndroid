package com.drunkbatya.vunder.feature.downloads

import androidx.lifecycle.ViewModel
import com.drunkbatya.vunder.core.cache.DownloadCenter
import com.drunkbatya.vunder.core.cache.DownloadJob
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DownloadsViewModel @Inject constructor(private val downloads: DownloadCenter) : ViewModel() {
    val jobs: StateFlow<List<DownloadJob>> = downloads.jobs

    fun cancel(job: DownloadJob) = downloads.cancel(job)

    fun clearFinished() = downloads.clearFinished()
}
