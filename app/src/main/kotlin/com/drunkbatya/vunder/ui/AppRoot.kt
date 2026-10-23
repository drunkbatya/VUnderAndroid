package com.drunkbatya.vunder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.feature.login.LoginFlow
import com.drunkbatya.vunder.ui.challenge.ChallengeHost
import com.drunkbatya.vunder.ui.main.MainScreen

@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AppState.Loading -> Unit
        AppState.SignedOut -> LoginFlow()
        is AppState.SignedIn -> MainScreen(userId = current.userId)
    }
    ChallengeHost()
}
