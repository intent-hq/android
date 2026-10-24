package dev.intent.core.protocol

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.internal.ws.RealWebSocket
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WssTransportTest {
    private val certificate = HeldCertificate.Builder().commonName("test daemon").build()
    private val fingerprint = CertificateFingerprint.of(certificate.certificate)

    @Test
    fun `correct leaf connects authenticates and exchanges envelopes`() {
        server().use { server ->
            val messages = LinkedBlockingQueue<String>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        messages.add(text)
                        webSocket.send(text)
                    }
                })
            )
            val events = Events()
            transport(server, fingerprint, events).use { transport ->
                transport.connect()
                assertEquals("open", events.next())
                assertEquals(
                    "Bearer secret",
                    server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization")
                )
                val message = JsonRpcMessage.Notification("events.event")
                assertTrue(transport.send(message))
                assertEquals(JsonRpc.encode(message), messages.poll(5, TimeUnit.SECONDS))
                assertEquals(message, events.next())
                transport.disconnect()
                assertFalse(transport.send(message))
            }
        }
    }

    @Test
    fun `mismatched certificate fails before HTTP auth`() {
        server().use { server ->
            val events = Events()
            transport(server, "00".repeat(32), events).use { transport ->
                transport.connect()
                assertTrue(events.next() is Throwable)
                assertEquals(0, server.requestCount)
            }
        }
    }

    @Test
    fun `missing fingerprint and plaintext are rejected before dialing`() {
        server().use { server ->
            assertThrows(IllegalArgumentException::class.java) { transport(server, "", Events()) }
            assertThrows(IllegalArgumentException::class.java) {
                WssTransport("ws://127.0.0.1:1234/ws", "secret", fingerprint, Events())
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `first use probe returns leaf without HTTP or auth`() {
        server().use { server ->
            assertEquals(fingerprint, CertificateFingerprint.probe(server.hostName, server.port))
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `server pings receive pongs and client pings receive pongs`() {
        server().use { server ->
            val sockets = LinkedBlockingQueue<RealWebSocket>()
            server.enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        sockets.add(webSocket as RealWebSocket)
                    }
                })
            )
            val events = Events()
            transport(server, fingerprint, events, heartbeatMillis = 50).use { transport ->
                transport.connect()
                assertEquals("open", events.next())
                val peer = sockets.poll(5, TimeUnit.SECONDS)!!
                // OkHttp exposes no public ping sender. Invoke its test peer only.
                peer.javaClass.methods.single { it.name.startsWith("writePingFrame") }.invoke(peer)
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while ((peer.receivedPongCount() == 0 || peer.receivedPingCount() < 2) &&
                    System.nanoTime() < deadline
                ) {
                    Thread.sleep(10)
                }
                assertTrue(peer.receivedPongCount() > 0)
                // A second client ping proves the first pong was accepted, not a heartbeat timeout.
                assertTrue(peer.receivedPingCount() >= 2)
                assertNull(events.queue.poll())
            }
        }
    }

    @Test
    fun `remote close permits a fresh authenticated connection`() {
        server().use { server ->
            server.enqueue(
                MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.close(1001, "Server shutting down")
                    }
                })
            )
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
            val events = Events()
            transport(server, fingerprint, events).use { transport ->
                transport.connect()
                assertEquals("open", events.next())
                assertEquals(1001, events.next())
                transport.connect()
                assertEquals("open", events.next())
                repeat(2) {
                    assertEquals(
                        "Bearer secret",
                        server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization")
                    )
                }
            }
        }
        assertEquals(
            listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L),
            (0..6).map(::reconnectDelayMillis)
        )
        assertEquals(30000L, reconnectDelayMillis(Int.MAX_VALUE))
    }

    private fun server(): MockWebServer = MockWebServer().apply {
        val certificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        useHttps(certificates.sslSocketFactory(), false)
        start()
    }

    private fun transport(
        server: MockWebServer,
        pin: String,
        events: Events,
        heartbeatMillis: Long = 30_000
    ) = WssTransport(
        server.url("/ws").toString().replace("https:", "wss:"),
        "secret",
        pin,
        events,
        heartbeatMillis
    )

    private class Events : WssTransport.Listener {
        val queue = LinkedBlockingQueue<Any>()
        fun next(): Any = checkNotNull(queue.poll(5, TimeUnit.SECONDS)) {
            "Transport event timed out"
        }
        override fun onOpen() {
            queue.add("open")
        }
        override fun onMessage(message: JsonRpcMessage) {
            queue.add(message)
        }
        override fun onClosed(code: Int, reason: String) {
            queue.add(code)
        }
        override fun onFailure(error: Throwable) {
            queue.add(error)
        }
    }
}
