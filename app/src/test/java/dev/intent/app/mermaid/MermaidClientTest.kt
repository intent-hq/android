package dev.intent.app.mermaid

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MermaidClientTest {
    @Test
    fun `client serves packaged HTML and refuses all remote resources and navigation`() {
        val view = WebView(RuntimeEnvironment.getApplication())
        var ready = 0
        val client = MermaidClient { ready++ }
        try {
            val html = client.shouldInterceptRequest(view, request(MERMAID_PAGE))
            assertEquals("text/html", html.mimeType)
            assertTrue(
                html.data.bufferedReader().use {
                    it.readText()
                }.contains("Content-Security-Policy")
            )
            for (url in listOf(
                "https://example.com/image.png",
                "file:///etc/hosts",
                "content://private/file"
            )) {
                val response = client.shouldInterceptRequest(view, request(url))
                assertEquals(0, response.data.use { it.readBytes() }.size)
                assertTrue(client.shouldOverrideUrlLoading(view, request(url)))
                client.onPageFinished(view, url)
            }
            assertEquals(0, ready)
            client.onPageFinished(view, MERMAID_PAGE)
            assertEquals(1, ready)
        } finally {
            view.destroy()
        }
    }

    private fun request(value: String): WebResourceRequest = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(value)
        override fun isForMainFrame(): Boolean = false
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }
}
