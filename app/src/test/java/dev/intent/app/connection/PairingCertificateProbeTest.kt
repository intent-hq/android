package dev.intent.app.connection

import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.tailcat.TunnelClient
import dev.intent.tailcat.TunnelClientFactory
import dev.intent.tailcat.TunnelForwarder
import dev.intent.tailcat.TunnelUnavailableException
import dev.intent.tailcat.UnavailableTunnelClientFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingCertificateProbeTest {
    private val payload = PairingPayload(emptyList(), 5181, "bearer-secret", tcAddress = "tunnel")

    @Test
    fun tunnelProbeUsesLoopbackAndClosesForwarderBeforeClient() {
        val events = mutableListOf<String>()
        val result = probePairingCertificate(payload, tunnels(events)) { host, port ->
            assertEquals("127.0.0.1", host)
            assertEquals(12345, port)
            events.add("probe")
            "fingerprint"
        }
        assertEquals("fingerprint", result)
        assertEquals(
            listOf("create", "ping", "forward", "probe", "close-forward", "close-client"),
            events
        )
    }

    @Test
    fun failedProbeStillClosesTunnelResources() {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            probePairingCertificate(payload, tunnels(events)) { _, _ -> error("No certificate") }
        }
        assertEquals(listOf("create", "ping", "forward", "close-forward", "close-client"), events)
    }

    @Test
    fun unavailableTunnelNeverProbesAndDirectRouteRemainsUsable() {
        assertThrows(TunnelUnavailableException::class.java) {
            probePairingCertificate(payload, UnavailableTunnelClientFactory) { _, _ ->
                error("Must not probe")
            }
        }
        assertEquals(
            "fingerprint",
            probePairingCertificate(
                payload.copy(hosts = listOf("[::1]")),
                UnavailableTunnelClientFactory
            ) { host, port ->
                assertEquals("::1", host)
                assertEquals(5181, port)
                "fingerprint"
            }
        )
    }

    private fun tunnels(events: MutableList<String>) = object : TunnelClientFactory {
        override val available = true
        override fun createClient(token: String): TunnelClient {
            assertEquals("tunnel", token)
            events.add("create")
            return object : TunnelClient {
                override fun ping(timeoutMs: Int) {
                    events.add("ping")
                }
                override fun forward(remotePort: Int): TunnelForwarder {
                    assertEquals(5181, remotePort)
                    events.add("forward")
                    return object : TunnelForwarder {
                        override val localPort = 12345
                        override fun close() {
                            events.add("close-forward")
                        }
                    }
                }
                override fun close() {
                    events.add("close-client")
                }
            }
        }
    }
}
