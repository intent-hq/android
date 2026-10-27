package dev.intent.app.files

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Each visible page owns one bounded bitmap; no document-sized bitmap cache. */
@Composable
internal fun WorkspacePdfPreview(file: File, modifier: Modifier = Modifier) {
    var count by remember(file) { mutableStateOf<Int?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        val result =
            withContext(Dispatchers.IO) { runCatching { openPdf(file).use { it.pageCount } } }
        count = result.getOrNull()
        failed = result.isFailure
    }
    if (failed) Text("Cannot open PDF. Use Open in another app.")
    LazyColumn(modifier.fillMaxSize().testTag("file.pdf")) {
        items(count ?: 0) { index -> PdfPage(file, index) }
    }
}

@Composable
private fun PdfPage(file: File, index: Int) {
    var bitmap by remember(file, index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file, index) { mutableStateOf(false) }
    LaunchedEffect(file, index) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                openPdf(file).use { renderer ->
                    renderer.openPage(index).use { page ->
                        val ratio = PDF_EDGE.toFloat() / maxOf(page.width, page.height)
                        Bitmap.createBitmap(
                            (page.width * ratio).toInt().coerceAtLeast(1),
                            (page.height * ratio).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        ).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
        bitmap = result.getOrNull()
        failed = result.isFailure
    }
    Column(Modifier.height(PDF_PAGE_HEIGHT.dp)) {
        Text("Page ${index + 1}")
        bitmap?.let { WorkspaceImagePreview(it, Modifier.weight(1f)) }
        if (failed) Text("Cannot render this page.")
    }
}

private fun openPdf(file: File): PdfRenderer {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    var transferred = false
    return try {
        PdfRenderer(descriptor).also { transferred = true }
    } finally {
        if (!transferred) descriptor.close()
    }
}

@Composable
internal fun WorkspaceHtmlPreview(text: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize().testTag("file.html"),
        factory = { context ->
            WebView(context).apply {
                contentDescription = "HTML preview"
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = InertHtmlClient(HTML_PREFIX + text)
                loadUrl(HTML_PAGE)
            }
        },
        onRelease = {
            it.stopLoading()
            it.destroy()
        }
    )
}

internal class InertHtmlClient(private val document: String) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        true

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse {
        if (request.isForMainFrame && request.url.toString() == HTML_PAGE) {
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                ByteArrayInputStream(document.toByteArray())
            )
        }
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
    }
}

private const val PDF_EDGE = 1536
private const val PDF_PAGE_HEIGHT = 600
private const val HTML_PREFIX = "<!doctype html><head>" +
    "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
    "style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'\">" +
    "<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">" +
    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"

private const val HTML_PAGE = "https://preview.intent.invalid/document"
