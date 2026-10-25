package dev.intent.app.connection

import dev.intent.core.protocol.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionPresentationTest {
    @Test
    fun nameUsesReadableHostWhileAddressKeepsTheEndpoint() {
        val connection = saved(listOf("192.168.1.2", "sams-mac.local"))
        assertEquals("Sams mac", connection.displayName)
        assertEquals("192.168.1.2:443", connection.displayAddress)
    }

    @Test
    fun numericAndTunnelConnectionsHaveSafeFallbackNames() {
        val numeric = saved(listOf("192.168.1.2"))
        val tunnel = saved(emptyList())
        assertEquals("Saved computer", numeric.displayName)
        assertEquals("Saved computer", tunnel.displayName)
        assertEquals("Tunnel connection", tunnel.displayAddress)
    }

    @Test
    fun ipv6AddressHasExactlyOnePairOfBrackets() {
        assertEquals("[::1]:443", saved(listOf("::1")).displayAddress)
        assertEquals("[::1]:443", saved(listOf("[::1]")).displayAddress)
    }

    @Test
    fun onlyTheActiveConnectedHostHasAConnectedBadge() {
        val state = ConnectionState(activeId = "one", status = "Connected")
        assertEquals("Connected", connectionStatus("one", state))
        assertEquals("Disconnected", connectionStatus("two", state))
        assertEquals("Disconnected", connectionStatus("one", state.copy(activeId = null)))
        assertEquals("Connecting", connectionStatus("one", state.copy(status = "Connecting")))
        assertEquals(
            "Disconnected · Retrying",
            connectionStatus("one", state.copy(status = "Connection lost. Retrying."))
        )
    }

    private fun saved(hosts: List<String>) = SavedConnection(
        "one",
        PairingPayload(hosts, 443, "secret-token", tcAddress = "private-tunnel")
    )
}
