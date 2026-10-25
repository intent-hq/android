package dev.intent.core.protocol.browser

import dev.intent.core.protocol.CertificateFingerprint
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedTunnelTest {
    @Test
    fun `binary socket uses pinned identity and header auth`() {
        val cert = HeldCertificate.Builder().commonName("daemon").build()
        server(cert).use { server ->
            server.enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        webSocket.send(bytes)
                    }
                })
            )
            val events = Events()
            pinnedTunnelTransport(
                server.url("/tunnel").toString().replace("https:", "wss:"),
                "secret",
                CertificateFingerprint.of(cert.certificate)
            )(events).use { socket ->
                socket.connect()
                assertEquals("open", events.next())
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("/tunnel", request.path)
                assertEquals("Bearer secret", request.getHeader("Authorization"))
                val bytes = TunnelFrame(TunnelFrame.OPEN, 1, byteArrayOf(0, 80)).encode()
                assertTrue(socket.send(bytes))
                assertArrayEquals(bytes, events.next() as ByteArray)
            }
        }
    }

    @Test
    fun `wrong tunnel pin fails before sending credentials`() {
        server(HeldCertificate.Builder().commonName("daemon").build()).use { server ->
            val events = Events()
            pinnedTunnelTransport(
                server.url("/tunnel").toString().replace("https:", "wss:"),
                "secret",
                "00".repeat(32)
            )(events).use { socket ->
                socket.connect()
                assertEquals("failed", events.next())
                assertEquals(0, server.requestCount)
            }
        }
    }

    private fun server(cert: HeldCertificate) = MockWebServer().apply {
        useHttps(
            HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),
            false
        )
        start()
    }
    private class Events : TunnelTransport.Listener {
        private val queue = LinkedBlockingQueue<Any>()
        override fun opened() {
            queue.add("open")
        }
        override fun received(bytes: ByteArray) {
            queue.add(bytes)
        }
        override fun failed() {
            queue.add("failed")
        }
        fun next(): Any = checkNotNull(queue.poll(5, TimeUnit.SECONDS))
    }
}
