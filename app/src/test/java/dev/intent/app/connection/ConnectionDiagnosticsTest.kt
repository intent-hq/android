package dev.intent.app.connection

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosticsTest {
    @Test
    fun `report records event time rather than export time in UTC`() {
        val diagnostics = ConnectionDiagnostics(
            Clock.fixed(Instant.parse("2026-10-26T02:00:01Z"), ZoneId.of("America/Los_Angeles"))
        )
        diagnostics.record(ConnectionEvent.READY)
        assertTrue(diagnostics.report().contains("1: 2026-10-26T02:00:01Z READY"))
    }
}
