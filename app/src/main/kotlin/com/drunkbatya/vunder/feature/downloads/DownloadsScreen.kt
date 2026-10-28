package com.drunkbatya.vunder.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.cache.DownloadJob
import com.drunkbatya.vunder.core.cache.DownloadKind
import com.drunkbatya.vunder.core.cache.DownloadState
import com.drunkbatya.vunder.core.cache.TrackExporter
import com.drunkbatya.vunder.ui.components.BackTopBar
import com.drunkbatya.vunder.ui.components.StatusText

@Composable
fun DownloadsScreen(onBack: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    DownloadsContent(jobs, onCancel = viewModel::cancel, onClearFinished = viewModel::clearFinished, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsContent(
    jobs: List<DownloadJob>,
    onCancel: (DownloadJob) -> Unit,
    onClearFinished: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.downloads_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onClearFinished, enabled = jobs.any { it.state.isFinished }) {
                        Text(stringResource(R.string.downloads_clear))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(jobs, key = DownloadJob::id) { job -> DownloadRow(job, onCancel = { onCancel(job) }) }
            }
            if (jobs.isEmpty()) StatusText(stringResource(R.string.downloads_empty))
        }
    }
}

@Composable
private fun DownloadRow(job: DownloadJob, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${job.track.artist} - ${job.track.title}", style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                statusText(job),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            val state = job.state
            if (state is DownloadState.Running) {
                val fraction = state.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (!job.state.isFinished) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Cancel, contentDescription = stringResource(R.string.downloads_cancel))
            }
        }
    }
}

@Composable
private fun statusText(job: DownloadJob): String {
    val kind = stringResource(job.kind.title)
    return when (val state = job.state) {
        DownloadState.Queued -> stringResource(R.string.downloads_waiting, kind)
        is DownloadState.Running -> state.fraction?.let { stringResource(R.string.downloads_running_percent, kind, (it * 100).toInt()) }
            ?: stringResource(R.string.downloads_running, kind)
        DownloadState.Done -> when (job.kind) {
            DownloadKind.Cache -> stringResource(R.string.downloads_saved_offline)
            DownloadKind.MusicFolder -> stringResource(R.string.downloads_saved_music_folder, TrackExporter.fileName(job.track))
        }
        is DownloadState.Failed -> stringResource(R.string.downloads_failed, kind, state.message)
        DownloadState.Cancelled -> stringResource(R.string.downloads_cancelled, kind)
    }
}
