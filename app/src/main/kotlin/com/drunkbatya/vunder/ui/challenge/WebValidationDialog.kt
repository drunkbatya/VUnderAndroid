package com.drunkbatya.vunder.ui.challenge

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.drunkbatya.vunder.R
import com.drunkbatya.vunder.core.network.ValidationOutcome
import com.drunkbatya.vunder.core.network.VkClientIdentity

private const val WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.7559.133 Mobile Safari/537.36"

private val oauthHosts = setOf(VkClientIdentity.OAUTH_HOST, "oauth.vk.com")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebValidationDialog(url: String, onFinish: (ValidationOutcome?) -> Unit) {
    Dialog(
        onDismissRequest = { onFinish(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.challenge_verification_title)) },
                    navigationIcon = {
                        IconButton(onClick = { onFinish(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.challenge_cancel))
                        }
                    },
                )
            },
        ) { padding ->
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = WEB_USER_AGENT
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url
                                if (target.host !in oauthHosts || target.path != "/blank.html") return false
                                onFinish(validationOutcome(target))
                                return true
                            }

                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                onFinish(null)
                                return true
                            }
                        }
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().removeAllCookies { loadUrl(url) }
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

private fun validationOutcome(blankPage: Uri): ValidationOutcome? {
    val fields = blankPage.toString().replace('#', '?').toUri()
    if (fields.getQueryParameter("cancel") != null || fields.getQueryParameter("success") == null) return null
    return ValidationOutcome(
        accessToken = fields.getQueryParameter("access_token"),
        userId = fields.getQueryParameter("user_id")?.toLongOrNull(),
    )
}
