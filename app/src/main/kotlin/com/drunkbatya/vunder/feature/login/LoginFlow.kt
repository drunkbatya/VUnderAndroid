package com.drunkbatya.vunder.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import com.drunkbatya.vunder.ui.navigation.StackNavDisplay

@Composable
fun LoginFlow(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StackNavDisplay(
        backStack = state.backStack,
        onBack = viewModel::cancel,
        entryProvider = entryProvider {
            entry<LoginRoute.Credentials> {
                CredentialsScreen(state, onSignIn = viewModel::signIn)
            }
            entry<LoginRoute.OneTimeCode> {
                OneTimeCodeScreen(
                    state = state,
                    onCodeChange = viewModel::changeCode,
                    onSubmit = viewModel::submitCode,
                    onSend = viewModel::sendCode,
                    onBack = viewModel::cancel,
                )
            }
            entry<LoginRoute.Password> {
                PasswordScreen(state, onSubmit = viewModel::submitPassword, onBack = viewModel::cancel)
            }
        },
    )
}
