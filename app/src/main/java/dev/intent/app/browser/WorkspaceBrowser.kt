package dev.intent.app.browser

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.intentPageInsets
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.browser.PreviewUrl
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceBrowser(session: RpcSession, workspaceId: String, onBack: () -> Unit) {
    val store = remember(session, workspaceId) { BrowserStore(session) }
    DisposableEffect(store) { onDispose { store.close() } }
    val state by store.state.collectAsState()
    BrowserScreen(state, onBack, store::open) {
        key(state.revision) {
            state.url?.let { url ->
                PreviewWebView(
                    url,
                    { store.loaded(state.revision) },
                    { store.failed(state.revision) },
                    store::open
                )
            }
        }
    }
}

@Composable
internal fun BrowserScreen(
    state: BrowserState,
    onBack: () -> Unit,
    open: (String) -> Unit,
    page: @Composable () -> Unit
) {
    var address by remember { mutableStateOf("http://daemon.localhost:3000/") }
    Column(Modifier.fillMaxSize()) {
        CompactAppBar("Browser preview", onBack)
        Column(
            Modifier.intentPageInsets(),
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
            ) {
                OutlinedTextField(
                    address,
                    { address = it },
                    Modifier.weight(1f),
                    label = { Text("Preview URL") },
                    singleLine = true
                )
                Button(onClick = { open(address) }, enabled = address.isNotBlank()) {
                    Text(if (state.error == null) "Open" else "Retry")
                }
            }
            if (state.loading) Text("Loading preview…", color = IntentScreen.secondaryText)
            if (state.error != null) {
                IntentSection("Preview unavailable") {
                    Text(state.error, color = IntentScreen.error)
                }
            }
            Text(
                "Start the preview server in this workspace on the host, then open its URL here.",
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Under development. Use relative resource URLs. HTTPS requires a valid certificate.",
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.error == null) page()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewWebView(
    url: String,
    loaded: () -> Unit,
    failed: () -> Unit,
    open: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = try {
                        val target = PreviewUrl.parse(request.url.toString())
                        val currentPort = PreviewUrl.parse(url).port
                        if (PreviewUrl.remotePort(target, setOf(currentPort)) != null) {
                            open(target.toASCIIString())
                            true
                        } else {
                            false
                        }
                    } catch (_: Exception) {
                        true
                    }
                    override fun onPageFinished(view: WebView, url: String) = loaded()
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame) failed()
                    }
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        response: WebResourceResponse
                    ) {
                        if (request.isForMainFrame) failed()
                    }
                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: SslError
                    ) {
                        handler.cancel()
                        failed()
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = {
            it.stopLoading()
            it.destroy()
        }
    )
}

@Preview
@Composable
fun BrowserScreenPreview() {
    MaterialTheme { BrowserScreen(BrowserState(loading = true), {}, {}) {} }
}
