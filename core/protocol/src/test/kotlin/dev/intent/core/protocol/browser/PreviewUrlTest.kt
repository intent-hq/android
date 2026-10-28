package dev.intent.core.protocol.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewUrlTest {
    @Test
    fun `local aliases and all valid ports route through the host tunnel`() {
        val hosts = listOf("localhost", "daemon.localhost", "127.0.0.1", "[::1]", "0.0.0.0", "[::]")
        hosts.forEach { host ->
            listOf(1, 3000, 65535).forEach { port ->
                val url = "http://$host:$port/a%20b?q=%2F#part"
                assertTrue(url, PreviewUrl.isHostLocal(url))
                assertEquals(port, PreviewUrl.remotePort(PreviewUrl.parse(url)))
            }
        }
        assertTrue(PreviewUrl.isHostLocal("HTTPS://LOCALHOST/path"))
        assertEquals(443, PreviewUrl.remotePort(PreviewUrl.parse("https://localhost/path")))
        assertEquals(80, PreviewUrl.remotePort(PreviewUrl.parse("http://localhost/path")))
    }

    @Test
    fun `external and deceptive names do not route to the host`() {
        listOf(
            "https://example.com/localhost", "https://localhost.example.com:3000",
            "https://daemon.localhost.example.com", "https://example.com/?url=http://localhost",
            "http://192.168.1.1", "http://[2001:db8::1]", "file://localhost/a",
            "javascript:alert(1)", "localhost:3000", "not a URL"
        ).forEach { assertFalse(it, PreviewUrl.isHostLocal(it)) }
    }

    @Test
    fun `invalid local addresses stay inside preview validation rather than opening externally`() {
        assertTrue(PreviewUrl.isHostLocal("http://user:pass@localhost:3000/"))
        assertTrue(PreviewUrl.isHostLocal("http://localhost:0/"))
        assertTrue(PreviewUrl.isHostLocal("http://localhost:65536/"))
    }

    @Test
    fun `only exact proxy loopback is exempt from forwarding`() {
        assertEquals(
            null,
            PreviewUrl.remotePort(PreviewUrl.parse("http://127.0.0.1:4000"), setOf(4000))
        )
        assertEquals(
            4000,
            PreviewUrl.remotePort(PreviewUrl.parse("http://daemon.localhost:4000"), setOf(4000))
        )
        assertEquals(
            4000,
            PreviewUrl.remotePort(PreviewUrl.parse("http://[::1]:4000"), setOf(4000))
        )
    }
}
