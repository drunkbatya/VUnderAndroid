package com.drunkbatya.vunder.ui.track

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow

sealed interface UiText {
    data class Resource(@StringRes val id: Int, val arguments: List<Any> = emptyList()) : UiText
    data class Plain(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Resource -> context.getString(id, *arguments.toTypedArray())
        is Plain -> text
    }

    companion object {
        fun failure(failure: Throwable): UiText = Plain(failure.message ?: failure.javaClass.simpleName)
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> stringResource(id, *arguments.toTypedArray())
    is UiText.Plain -> text
}

@Composable
fun NoticeToasts(notices: Flow<UiText>) {
    val context = LocalContext.current
    LaunchedEffect(notices) {
        notices.collect { Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show() }
    }
}
