package dev.intent.core.protocol.pairing

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualConnectionTargetTest {
    private val blob = "tc" + Base64.getUrlEncoder().withoutPadding().encodeToString(
        byteArrayOf(0xa1.toByte(), 0x61, 0x70, 0x58, 0x20) + ByteArray(32) { it.toByte() }
    )

    @Test
    fun classifiesPublicKeyBlobAsTunnelOnlyAndRetainsPin() {
        val input = ManualPairingInput(" $blob\n", "5181", "secret", "AB".repeat(32))
        val payload = input.toPayload()
        assertTrue(payload.hosts.isEmpty())
        assertEquals(blob, payload.tcAddress)
        assertEquals(5181, payload.port)
        assertEquals("AB".repeat(32), payload.certFingerprint)
        assertFalse(input.toString().contains(blob))
    }

    @Test
    fun tailcatLikeDnsNamesStayDirect() {
        listOf("tcp-server.local", "tchost", "tcoA", "tc-key-abc123", "tco.example.com").forEach {
            val payload = ManualPairingInput(it, "5181", "secret").toPayload()
            assertEquals(listOf(it), payload.hosts)
            assertNull(payload.tcAddress)
        }
    }

    @Test
    fun classifierRequiresUnpaddedBase64UrlAndPublicKeyHeader() {
        val wrongHeader = "tc" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(37))
        listOf("tc", "tcoA", "$blob=", blob.uppercase(), "$blob/", wrongHeader).forEach {
            assertFalse(it, isTailcatAddress(it))
        }
    }

    @Test
    fun explicitTunnelCanAccompanyDirectHostsButCannotConflictWithClassifiedTarget() {
        val payload = ManualPairingInput("one.local, two.local", "5181", "secret", tcAddress = blob)
            .toPayload()
        assertEquals(listOf("one.local", "two.local"), payload.hosts)
        assertEquals(blob, payload.tcAddress)
        assertThrows(IllegalArgumentException::class.java) {
            ManualPairingInput(blob, "5181", "secret", tcAddress = "other").toPayload()
        }
    }
}
