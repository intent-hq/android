package dev.intent.app.connection

import dev.intent.core.protocol.pairing.PairingPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRecoveryTest {
    @Test
    fun `refresh merges routes without accepting credentials or invalid hosts`() {
        val original = PairingPayload(listOf("desktop.local"), 443, "SECRET", "AB".repeat(32))
        val refreshed = refreshedPayload(
            original,
            Json.parseToJsonElement(
                """{"localIps":["DESKTOP.local","10.0.0.2","::1","bad/path",3],
                "token":"evil","certFingerprint":"evil","port":80,"tcAddress":" tunnel "}"""
            )
        )
        assertEquals(listOf("desktop.local", "10.0.0.2", "::1"), refreshed.hosts)
        assertEquals("tunnel", refreshed.tcAddress)
        assertEquals(original.token, refreshed.token)
        assertEquals(original.certFingerprint, refreshed.certFingerprint)
        assertEquals(original.port, refreshed.port)
        assertEquals(original, refreshedPayload(original, Json.parseToJsonElement("{}")))
    }

    @Test
    fun `diagnostics exports only bounded fixed events and clears history`() {
        val diagnostics = ConnectionDiagnostics()
        repeat(250) { diagnostics.record(ConnectionEvent.CONNECTING) }
        diagnostics.record(ConnectionEvent.HOST_REFRESH_FAILED)
        val report = diagnostics.report()
        assertEquals(200, report.lines().count { it.contains(": ") })
        assertTrue(report.contains("HOST_REFRESH_FAILED"))
        listOf("SECRET", "AB".repeat(32), "desktop.local", "principal-a").forEach {
            assertFalse(report.contains(it))
        }
        diagnostics.clear()
        assertFalse(diagnostics.report().contains("HOST_REFRESH_FAILED"))
        assertTrue(report.contains("HOST_REFRESH_FAILED"))
    }
}
