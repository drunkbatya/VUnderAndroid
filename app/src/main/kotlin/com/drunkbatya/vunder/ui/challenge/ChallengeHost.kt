package com.drunkbatya.vunder.ui.challenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drunkbatya.vunder.core.network.Challenge
import com.drunkbatya.vunder.core.network.ChallengeCoordinator
import com.drunkbatya.vunder.core.network.ValidationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ChallengeViewModel @Inject constructor(private val coordinator: ChallengeCoordinator) : ViewModel() {
    val pending: StateFlow<Challenge?> = coordinator.pending

    fun submitCaptcha(challenge: Challenge.Captcha, key: String?) = coordinator.submitCaptcha(challenge, key)

    fun finishValidation(challenge: Challenge.Validation, outcome: ValidationOutcome?) =
        coordinator.finishValidation(challenge, outcome)
}

@Composable
fun ChallengeHost(viewModel: ChallengeViewModel = hiltViewModel()) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    when (val challenge = pending) {
        is Challenge.Captcha -> key(challenge) {
            CaptchaDialog(
                imageUrl = challenge.imageUrl,
                onSubmit = { viewModel.submitCaptcha(challenge, it) },
                onCancel = { viewModel.submitCaptcha(challenge, null) },
            )
        }
        is Challenge.Validation -> key(challenge) {
            WebValidationDialog(url = challenge.url, onFinish = { viewModel.finishValidation(challenge, it) })
        }
        null -> Unit
    }
}
