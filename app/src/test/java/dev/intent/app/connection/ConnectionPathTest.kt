package dev.intent.app.connection

import dev.intent.core.protocol.pairing.PairingPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionPathTest {
    @Test
    fun daemonNameUsesTrimmedPrettyNameThenHostnameAndNeverGuessesFromDns() {
        fun name(json: String) = reportedConnectionName(Json.parseToJsonElement(json))
        assertEquals("My Mac", name("""{"prettyHostname":" My Mac ","hostname":"machine"}"""))
        assertEquals("machine", name("""{"prettyHostname":"  ","hostname":"machine"}"""))
        assertNull(name("""{"prettyHostname":123,"hostname":null}"""))
        val saved = SavedConnection("one", PairingPayload(listOf("my-mac.local"), 443, "secret"))
        assertEquals("my-mac.local:443", saved.displayName)
        assertEquals(
            "my-mac.local:443",
            saved.copy(metadata = ConnectionMetadata("  ")).displayName
        )
        assertEquals(
            "Shared Mac",
            reportedConnectionName(
                Json.parseToJsonElement("""{"collaborationName":"Shared Mac","hostname":"raw"}"""),
                true
            )
        )
    }

    @Test
    fun knownPathsDeduplicateHostsAndHideTunnelCapabilities() {
        val saved = SavedConnection(
            "one",
            PairingPayload(
                listOf(
                    "MAC.local",
                    "mac.local",
                    "192.168.1.20",
                    "100.100.1.2",
                    "[fd7a:115c:a1e0::1]"
                ),
                443,
                "bearer-secret",
                tcAddress = "tc-secret-capability"
            )
        )
        val paths = saved.knownPaths()
        assertEquals(
            listOf("Direct LAN", "Direct LAN", "Direct VPN", "Direct VPN", "Tunnel"),
            paths.map {
                it.type
            }
        )
        assertFalse(paths.toString().contains("tc-secret-capability"))
        assertFalse(paths.toString().contains("bearer-secret"))
        assertEquals(tunnelPath("tc-secret-capability"), paths.last())
        assertEquals("[fd7a:115c:a1e0::1]:443", paths[3].address)
        assertEquals("Direct", directPath("example.com", 443).type)
    }

    @Test
    fun activePathRequiresTheConnectedRecordAndChangesWithTheWinner() {
        val direct = directPath("192.168.1.20", 443)
        val state = ConnectionState(activeId = "one", status = "Connected", activePath = direct)
        assertEquals(direct, state.pathInUse("one"))
        assertNull(state.pathInUse("two"))
        assertNull(state.copy(status = "Connecting").pathInUse("one"))
        assertNull(state.copy(status = "Connection lost. Retrying.").pathInUse("one"))
        val tunnel = tunnelPath("tc-secret")
        assertEquals("Tunnel", state.copy(activePath = tunnel).pathInUse("one")?.badge)
    }
}
