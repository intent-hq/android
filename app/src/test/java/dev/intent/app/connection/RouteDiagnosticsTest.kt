package dev.intent.app.connection

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDiagnosticsTest {
    @Test
    fun `exception messages and nested credentials never enter logs`() {
        val error =
            IOException(
                "token=secret tc=credentials fingerprint=bytes",
                IllegalStateException("secret")
            )
        assertEquals(
            "route=tunnel stage=ping outcome=failure exception=IOException cause=IllegalStateException",
            RouteDiagnostics.format("tunnel", "ping", "failure", error)
        )
    }
}
