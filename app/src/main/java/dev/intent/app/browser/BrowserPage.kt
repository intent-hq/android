package dev.intent.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.intent.core.protocol.browser.PreviewUrl
import java.net.URI

/** One WebView and tunnel survive page navigation so native history remains usable. */
internal class BrowserPage(private val context: Context, private val store: BrowserStore) {
    private var webView: WebView? = null
    private var revision = -1L
    private val origins = mutableMapOf<String, URI>()
    private val localPorts = mutableSetOf<Int>()

    @SuppressLint("SetJavaScriptEnabled")
    fun view(): WebView = webView ?: WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        webViewClient = client()
        webView = this
    }

    fun load(state: BrowserState) {
        val url = state.url ?: return
        // A pending forward keeps the old page mounted. Load only its resolved command.
        if (state.resolvedRevision == revision) return
        revision = state.resolvedRevision
        val original = state.requestedUrl?.let { runCatching { PreviewUrl.parse(it) }.getOrNull() }
        val forwarded = PreviewUrl.parse(url)
        if (original != null && original != forwarded) {
            origins[origin(forwarded)] = original
            localPorts.add(forwarded.port)
        }
        view().loadUrl(url)
    }

    fun back() {
        webView?.goBack()
    }
    fun forward() {
        webView?.goForward()
    }
    fun reload() {
        webView?.reload()
    }

    fun close() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        origins.clear()
        localPorts.clear()
    }

    private fun client() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            try {
                val target = PreviewUrl.parse(request.url.toString())
                if (PreviewUrl.remotePort(target, localPorts) != null) {
                    if (request.isForMainFrame) {
                        store.open(
                            target.toASCIIString()
                        )
                    } else {
                        store.failed(revision)
                    }
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                true
            }
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            store.loading(revision)
        }
        override fun onPageFinished(view: WebView, url: String) {
            if (view.url != url) return
            store.navigated(revision, address(url), view.canGoBack(), view.canGoForward())
            store.loaded(revision)
        }
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) store.failed(revision)
        }
        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            response: WebResourceResponse
        ) {
            if (request.isForMainFrame) store.failed(revision)
        }
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            store.failed(revision)
        }
    }

    private fun address(value: String): String = runCatching {
        val url = URI(value)
        val original = origins[origin(url)] ?: return value
        "${original.scheme}://${original.rawAuthority}" +
            (url.rawPath ?: "") + (url.rawQuery?.let { "?$it" } ?: "") +
            (url.rawFragment?.let { "#$it" } ?: "")
    }.getOrDefault(value)

    private fun origin(url: URI) = "${url.scheme}://${url.rawAuthority}"
}
