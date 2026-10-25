package com.drunkbatya.vunder.feature.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R

@Composable
internal fun CredentialsScreen(state: LoginUiState, onSignIn: (String) -> Unit) {
    var login by rememberSaveable { mutableStateOf(state.login) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canSubmit = !state.busy && login.isNotBlank()
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSignIn(login.trim())
        }
    }

    FormScaffold(title = stringResource(R.string.app_name), busy = state.busy, error = state.error, onBack = null) {
        Text(
            text = stringResource(R.string.login_heading),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        FormText(stringResource(R.string.login_subtitle))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text(stringResource(R.string.login_field_hint)) },
            enabled = !state.busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentType = ContentType.Username },
        )
        PrimaryButton(R.string.login_sign_in, enabled = canSubmit, onClick = submit)
    }

    LaunchedEffect(Unit) {
        if (login.isEmpty()) focusRequester.requestFocus()
    }
}
