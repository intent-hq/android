package dev.intent.app.connection

import dev.intent.core.protocol.CertificateFingerprint
import dev.intent.core.protocol.JsonRpc
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.SessionState
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRouteSecurityTest {
    private val certificate = HeldCertificate.Builder().commonName("daemon").build()
    private val pin = CertificateFingerprint.of(certificate.certificate)

    @Test
    fun `tunnel rejects a wrong pin before any HTTP credentials`() {
        server().use { server ->
            val events = LinkedBlockingQueue<Any>()
            val tunnel = FakeTunnel(server.port)
            HostRacingTransport(
                payload("00".repeat(32)),
                object : WssTransport.Listener {
                    override fun onOpen() {
                        events.add("open")
                    }
                    override fun onFailure(error: Throwable) {
                        events.add(error)
                    }
                },
                tunnel
            ).use { race ->
                race.connect()
                assertTrue(events.next() is Throwable)
                assertEquals(0, server.requestCount)
                assertEquals("forwarder", tunnel.closed.next())
                assertEquals("client", tunnel.closed.next())
            }
        }
    }

    @Test
    fun `tunnel-only session pins authenticates admits principal and shares route with browser`() {
        server().use { server ->
            server.enqueue(rpcUpgrade())
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
            val states = LinkedBlockingQueue<SessionState>()
            val tunnel = FakeTunnel(server.port)
            createConnectionSession(SavedConnection("test", payload(pin)), {
                states.add(it)
            }, tunnels = tunnel).use { session ->
                session.connect()
                assertEquals(SessionState.CONNECTING, states.next())
                assertEquals(SessionState.HANDSHAKING, states.next())
                assertEquals(SessionState.READY, states.next())
                assertEquals("person-a", session.principalId)
                session.openBrowser().get(3, TimeUnit.SECONDS).use { browser ->
                    browser.forward("https://example.com/").get(3, TimeUnit.SECONDS)
                    val rpc = checkNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                    val auxiliary = checkNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                    assertEquals("/ws", rpc.path)
                    assertEquals("/tunnel", auxiliary.path)
                    assertEquals("Bearer daemon-secret", rpc.getHeader("Authorization"))
                    assertEquals("Bearer daemon-secret", auxiliary.getHeader("Authorization"))
                }
            }
            assertEquals("forwarder", tunnel.closed.next())
            assertEquals("client", tunnel.closed.next())
        }
    }

    @Test
    fun `session uses refreshed routes but keeps the saved pin and bearer`() {
        server().use { server ->
            server.enqueue(rpcUpgrade())
            val saved = payload(
                pin
            ).copy(hosts = listOf("old.invalid"), tcAddress = null, port = server.port)
            val fresh = saved.copy(
                hosts = listOf("127.0.0.1"),
                token = "wrong",
                certFingerprint = "00".repeat(32)
            )
            val states = LinkedBlockingQueue<SessionState>()
            createConnectionSession(SavedConnection("test", saved), {
                states.add(it)
            }, { fresh }).use { session ->
                session.connect()
                assertEquals(SessionState.CONNECTING, states.next())
                assertEquals(SessionState.HANDSHAKING, states.next())
                assertEquals(SessionState.READY, states.next())
                assertEquals(
                    "Bearer daemon-secret",
                    server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Authorization")
                )
            }
        }
    }

    private fun payload(fingerprint: String) = PairingPayload(
        emptyList(),
        443,
        "daemon-secret",
        fingerprint,
        "tc-capability"
    )

    private fun server() = MockWebServer().apply {
        useHttps(
            HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),
            false
        )
        start()
    }

    private fun rpcUpgrade() = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val request = JsonRpc.decode(text) as JsonRpcMessage.Request
            val result = when (request.method) {
                "principal.me" -> JsonObject(mapOf("id" to JsonPrimitive("person-a")))
                "system.status" -> JsonObject(mapOf("protocolVersion" to JsonPrimitive("1.1.0")))
                else -> JsonObject(emptyMap())
            }
            webSocket.send(JsonRpc.encode(JsonRpcMessage.Success(request.id, result)))
        }
    })
}
