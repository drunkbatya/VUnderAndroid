package com.drunkbatya.vunder.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.BuildConfig
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.update.AppRelease
import com.drunkbatya.vunder.core.update.UpdateState

@Composable
fun UpdateHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        UpdateState.Idle, UpdateState.Checking -> Unit
        is UpdateState.Available -> AvailableDialog(
            release = current.release,
            allowsSkip = current.allowsSkip,
            onInstall = { viewModel.download(current.release) },
            onSkip = { viewModel.skip(current.release) },
            onLater = viewModel::dismiss,
        )
        UpdateState.UpToDate -> MessageDialog(
            title = stringResource(R.string.update_up_to_date_title),
            text = stringResource(
                R.string.update_up_to_date_text,
                stringResource(R.string.update_version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            ),
            onDismiss = viewModel::dismiss,
        )
        is UpdateState.Failed -> MessageDialog(
            title = stringResource(R.string.update_failed_title),
            text = current.reason,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun AvailableDialog(
    release: AppRelease,
    allowsSkip: Boolean,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.update_available_release, stringResource(R.string.app_name), release.tag))
                release.notes?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) } },
        dismissButton = {
            Row {
                if (allowsSkip) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.update_skip), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}

@Composable
private fun MessageDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_ok)) } },
    )
}
