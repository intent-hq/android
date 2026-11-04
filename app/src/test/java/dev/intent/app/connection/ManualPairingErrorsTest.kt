package dev.intent.app.connection

import dev.intent.core.protocol.pairing.ManualPairingInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPairingErrorsTest {
    @Test
    fun tunnelOnlyInputExplainsBuildSupportWithoutRejectingDirectFallback() {
        val tunnel = ManualPairingInput("", "5181", "secret", tcAddress = "tunnel")
        assertTrue(manualPairingErrors(tunnel, tunnelAvailable = true).isValid)
        val unsupported = manualPairingErrors(tunnel, tunnelAvailable = false)
        assertEquals(TUNNEL_UNAVAILABLE_MESSAGE, unsupported.host)
        assertNull(unsupported.port)
        assertNull(unsupported.token)
        assertTrue(
            manualPairingErrors(tunnel.copy(host = "desktop.local"), tunnelAvailable = false).isValid
        )
    }

    @Test
    fun emptyFieldsCannotSubmit() {
        val errors = manualPairingErrors(ManualPairingInput("", "", ""))
        assertNotNull(errors.host)
        assertNotNull(errors.port)
        assertNotNull(errors.token)
        assertNull(errors.fingerprint)
        assertFalse(errors.isValid)
    }

    @Test
    fun errorsStayWithTheirField() {
        val valid = ManualPairingInput("desktop.local", "443", "secret")
        val host = manualPairingErrors(valid.copy(host = "https://desktop.local"))
        assertNotNull(host.host)
        assertNull(host.port)
        assertNull(host.token)
        for (port in listOf("0", "65536", "abc")) {
            val errors = manualPairingErrors(valid.copy(port = port))
            assertNotNull(errors.port)
            assertNull(errors.host)
        }
        assertNotNull(manualPairingErrors(valid.copy(token = "bad\ntoken")).token)
    }

    @Test
    fun fingerprintIsOptionalButMustBeValidWhenPresent() {
        val valid = ManualPairingInput("desktop.local", "443", "secret")
        assertTrue(manualPairingErrors(valid).isValid)
        assertTrue(manualPairingErrors(valid.copy(certFingerprint = "ab".repeat(32))).isValid)
        assertTrue(
            manualPairingErrors(
                valid.copy(
                    certFingerprint = List(32) {
                        "AB"
                    }.joinToString(":")
                )
            ).isValid
        )
        assertNotNull(manualPairingErrors(valid.copy(certFingerprint = "invalid")).fingerprint)
    }
}
