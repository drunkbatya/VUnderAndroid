package com.drunkbatya.vunder.feature.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.drunkbatya.vunder.R

@Composable
internal fun PasswordScreen(state: LoginUiState, onSubmit: (String) -> Unit, onBack: () -> Unit) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canSubmit = !state.busy && password.isNotEmpty()
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit(password)
        }
    }

    FormScaffold(title = stringResource(R.string.login_password_title), busy = state.busy, error = state.error, onBack = onBack) {
        FormText(stringResource(R.string.login_password_prompt))
        OutlinedTextField(
            value = state.login,
            onValueChange = {},
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_hint)) },
            enabled = !state.busy,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentType = ContentType.Password },
        )
        PrimaryButton(R.string.login_sign_in, enabled = canSubmit, onClick = submit)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
