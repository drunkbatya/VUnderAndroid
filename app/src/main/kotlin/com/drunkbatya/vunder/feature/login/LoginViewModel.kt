package com.drunkbatya.vunder.feature.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drunkbatya.vunder.core.auth.AuthException
import com.drunkbatya.vunder.core.auth.AuthFlow
import com.drunkbatya.vunder.core.auth.AuthStep
import com.drunkbatya.vunder.core.auth.OneTimeCodeDelivery
import com.drunkbatya.vunder.core.network.VkApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginRoute {
    data object Credentials : LoginRoute
    data object OneTimeCode : LoginRoute
    data object Password : LoginRoute
}

sealed interface LoginError {
    data object WrongCode : LoginError
    data class Failure(val message: String) : LoginError
}

data class OneTimeCodeUiState(
    val method: String = "",
    val code: String = "",
    val resendable: Boolean = false,
    val sending: Boolean = false,
    val delivery: OneTimeCodeDelivery? = null,
)

data class LoginUiState(
    val backStack: List<LoginRoute> = listOf(LoginRoute.Credentials),
    val login: String = "",
    val busy: Boolean = false,
    val error: LoginError? = null,
    val oneTimeCode: OneTimeCodeUiState = OneTimeCodeUiState(),
)

@HiltViewModel
class LoginViewModel @Inject constructor(private val authFlow: AuthFlow) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var step: AuthStep? = null
    private var stepJob: Job? = null
    private var sendJob: Job? = null

    fun signIn(login: String) {
        _state.update { it.copy(login = login) }
        perform { authFlow.start(login) }
    }

    fun changeCode(code: String) = _state.update { it.copy(oneTimeCode = it.oneTimeCode.copy(code = code)) }

    fun submitCode() {
        val current = step as? AuthStep.OneTimeCode ?: return
        val code = state.value.oneTimeCode.code.trim()
        perform { authFlow.confirmCode(current, code) }
    }

    fun submitPassword(password: String) {
        val current = step as? AuthStep.Password ?: return
        perform {
            authFlow.submitPassword(current, password)
            AuthStep.SignedIn
        }
    }

    fun sendCode() {
        val current = step as? AuthStep.OneTimeCode ?: return
        sendJob?.cancel()
        updateCode { it.copy(sending = true) }
        sendJob = viewModelScope.launch {
            try {
                val delivery = authFlow.sendCode(current)
                updateCode { it.copy(delivery = delivery) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.e(TAG, "one-time code delivery failed: ${failure.message}")
                _state.update { it.copy(error = failure.toLoginError()) }
            }
            updateCode { it.copy(sending = false) }
        }
    }

    fun cancel() {
        Log.i(TAG, "sign in cancelled")
        reset(error = null)
    }

    private fun perform(action: suspend () -> AuthStep) {
        if (stepJob?.isActive == true) return
        _state.update { it.copy(busy = true, error = null) }
        stepJob = viewModelScope.launch {
            try {
                advance(action())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (rejected: AuthException.CodeRejected) {
                val error = if (rejected.error.isWrongCode) LoginError.WrongCode else LoginError.Failure(rejected.error.message)
                _state.update { it.copy(busy = false, error = error, oneTimeCode = it.oneTimeCode.copy(code = "")) }
            } catch (_: VkApiException.ChallengeCancelled) {
                cancel()
            } catch (failure: Exception) {
                Log.e(TAG, "sign in failed: ${failure.message}")
                reset(failure.toLoginError())
            }
        }
    }

    private fun advance(next: AuthStep) {
        step = next
        when (next) {
            is AuthStep.OneTimeCode -> {
                _state.update {
                    it.copy(
                        backStack = it.backStack + LoginRoute.OneTimeCode,
                        busy = false,
                        oneTimeCode = OneTimeCodeUiState(method = next.method, resendable = next.canResend),
                    )
                }
                if (next.deliversAutomatically) sendCode()
            }
            is AuthStep.Password -> _state.update { it.copy(backStack = it.backStack + LoginRoute.Password, busy = false) }
            AuthStep.SignedIn -> reset(error = null)
        }
    }

    private fun reset(error: LoginError?) {
        stepJob?.cancel()
        sendJob?.cancel()
        step = null
        _state.update { LoginUiState(login = it.login, error = error) }
    }

    private fun updateCode(transform: (OneTimeCodeUiState) -> OneTimeCodeUiState) =
        _state.update { it.copy(oneTimeCode = transform(it.oneTimeCode)) }

    private fun Exception.toLoginError() = LoginError.Failure(message ?: javaClass.name)

    private companion object {
        const val TAG = "Login"
    }
}
