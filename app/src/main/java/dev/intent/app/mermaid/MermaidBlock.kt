package dev.intent.app.mermaid

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

private const val DARK_LUMINANCE_THRESHOLD = 0.5f
private const val INITIAL_HEIGHT = 160
private const val RENDER_TIMEOUT_MS = 10_000L

/** False delegates to Markdown's literal code fallback, including incomplete streaming diagrams. */
@Composable
fun MermaidFence(language: String, code: String): Boolean {
    if (language != "mermaid" || code.isBlank() || code.length > MAX_SOURCE_LENGTH) return false
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD
    var failed by remember(code, dark) { mutableStateOf(false) }
    var expanded by remember(code) { mutableStateOf(false) }
    if (!failed) {
        key(code, dark) {
            androidx.compose.foundation.layout.Column {
                dev.intent.app.design.IntentTextButton(onClick = { expanded = true }) {
                    androidx.compose.material3.Text("Expand diagram")
                }
                MermaidView(code, dark) { failed = true }
            }
        }
    }
    if (expanded) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column {
                    dev.intent.app.design.IntentTextButton(onClick = { expanded = false }) {
                        androidx.compose.material3.Text("Close diagram")
                    }
                    MermaidView(code, dark, Modifier.weight(1f).fillMaxSize()) { failed = true }
                }
            }
        }
    }
    return !failed
}

// Only bundled scripts can load; all network requests are denied.
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidView(
    code: String,
    dark: Boolean,
    modifier: Modifier? = null,
    onFailure: () -> Unit
) {
    var height by remember { mutableIntStateOf(INITIAL_HEIGHT) }
    var complete by remember { mutableStateOf(false) }
    val lifetime = remember { MermaidLifetime() }
    DisposableEffect(Unit) { onDispose { lifetime.active = false } }
    LaunchedEffect(Unit) {
        delay(RENDER_TIMEOUT_MS)
        if (!complete) onFailure()
    }
    AndroidView(
        modifier = modifier ?: Modifier.fillMaxWidth().height(height.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                webViewClient = MermaidClient { view ->
                    view.evaluateJavascript(mermaidScript(code, dark)) { reply ->
                        if (lifetime.active) {
                            complete = true
                            val measured = mermaidHeight(reply)
                            if (measured == null) onFailure() else height = measured
                        }
                    }
                }
                loadUrl(MERMAID_PAGE)
            }
        },
        onRelease = {
            lifetime.active = false
            it.stopLoading()
            it.destroy()
        }
    )
}

private class MermaidLifetime {
    var active = true
}

internal class MermaidClient(private val onReady: (WebView) -> Unit) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        true

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse {
        val asset = mermaidAsset(request.url.toString())
        if (asset != null) {
            val mime = when (asset.substringAfterLast('.')) {
                "html" -> "text/html"
                "css" -> "text/css"
                else -> "application/javascript"
            }
            return WebResourceResponse(mime, "UTF-8", view.context.assets.open("mermaid/$asset"))
        }
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (url == MERMAID_PAGE) onReady(view)
    }
}
