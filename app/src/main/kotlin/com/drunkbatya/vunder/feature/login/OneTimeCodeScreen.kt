package com.drunkbatya.vunder.feature.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.drunkbatya.vunder.R

@Composable
internal fun OneTimeCodeScreen(
    state: LoginUiState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val oneTimeCode = state.oneTimeCode
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canSubmit = !state.busy && oneTimeCode.code.isNotBlank()
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit()
        }
    }

    FormScaffold(title = stringResource(R.string.login_code_title), busy = state.busy, error = state.error, onBack = onBack) {
        FormText(oneTimeCode.description())
        OutlinedTextField(
            value = oneTimeCode.code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.login_code_hint)) },
            enabled = !state.busy,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentType = ContentType.SmsOtpCode },
        )
        PrimaryButton(R.string.login_continue, enabled = canSubmit, onClick = submit)
        if (oneTimeCode.resendable) {
            TextButton(onClick = onSend, enabled = !oneTimeCode.sending, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (oneTimeCode.delivery == null) R.string.login_send_sms else R.string.login_resend))
            }
        }
    }

    LaunchedEffect(state.error) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun OneTimeCodeUiState.description(): String {
    val delivery = delivery ?: return stringResource(
        when (method) {
            "sms" -> R.string.login_code_method_sms
            "callreset" -> R.string.login_code_method_callreset
            "push" -> R.string.login_code_method_push
            "codegen" -> R.string.login_code_method_codegen
            "email" -> R.string.login_code_method_email
            "reserve_code" -> R.string.login_code_method_reserve_code
            else -> R.string.login_code_method_other
        },
    )
    val mask = delivery.phoneMask
    return when {
        delivery.codeLength > 0 && mask != null -> stringResource(R.string.login_code_call_sent_to, delivery.codeLength, mask)
        delivery.codeLength > 0 -> stringResource(R.string.login_code_call_sent, delivery.codeLength)
        mask != null -> stringResource(R.string.login_code_sms_sent_to, mask)
        else -> stringResource(R.string.login_code_sms_sent)
    }
}
