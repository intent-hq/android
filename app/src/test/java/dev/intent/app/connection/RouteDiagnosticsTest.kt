package dev.intent.app.connection

import dev.intent.tailcat.NativeTunnelException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDiagnosticsTest {
    @Test
    fun `native detail survives with credentials removed`() {
        val error =
            NativeTunnelException(
                "netmon.New: permission denied token=secret tcSensitiveCredential123456\nmore",
                "tcSensitiveCredential123456"
            )
        assertEquals(
            "route=tunnel stage=ping outcome=failure exception=NativeTunnelException cause=null" +
                " detail=netmon.New: permission denied token=[redacted] [redacted] more",
            RouteDiagnostics.format("tunnel", "ping", "failure", error)
        )
    }

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
