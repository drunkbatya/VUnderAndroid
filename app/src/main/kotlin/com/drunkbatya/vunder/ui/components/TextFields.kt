package com.drunkbatya.vunder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.drunkbatya.vunder.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun RoundedTextField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        state = state,
        modifier = modifier,
        textStyle = textStyle,
        lineLimits = lineLimits,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorator = { innerTextField ->
            Row(
                Modifier
                    .defaultMinSize(minHeight = 38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(containerColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (state.text.isEmpty()) {
                        Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun SearchField(state: TextFieldState, placeholder: String, modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    val focusManager = LocalFocusManager.current
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    RoundedTextField(
        state = state,
        placeholder = placeholder,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        onKeyboardAction = {
            focusManager.clearFocus()
            onSearch()
        },
        leading = { Icon(Icons.Rounded.Search, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp)) },
        trailing = {
            if (state.text.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Cancel,
                    contentDescription = stringResource(R.string.clear_query),
                    tint = iconTint,
                    modifier = Modifier.size(20.dp).clickable { state.clearText() },
                )
            }
        },
    )
}

fun TextFieldState.trimmedText(): Flow<String> = snapshotFlow { text.trim().toString() }.distinctUntilChanged()
