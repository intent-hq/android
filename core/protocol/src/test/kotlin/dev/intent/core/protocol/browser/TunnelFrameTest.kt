package dev.intent.core.protocol.browser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelFrameTest {
    @Test
    fun `binary headers are big endian and validate payloads`() {
        val bytes = TunnelFrame(
            TunnelFrame.OPEN,
            0x10203040,
            byteArrayOf(0x1f, 0x90.toByte())
        ).encode()
        assertArrayEquals(byteArrayOf(1, 0x10, 0x20, 0x30, 0x40, 0x1f, 0x90.toByte()), bytes)
        assertEquals(0x10203040, TunnelFrame.decode(bytes).streamId)
        listOf(
            byteArrayOf(1),
            byteArrayOf(99, 0, 0, 0, 1),
            TunnelFrame(TunnelFrame.OPEN_OK, 1, byteArrayOf(1)).encode(),
            TunnelFrame(TunnelFrame.CREDIT, 1, byteArrayOf(0, 0, 0, 0)).encode(),
            TunnelFrame(TunnelFrame.OPEN, 1).encode()
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { TunnelFrame.decode(it) }
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            TunnelFrame.decode(
                TunnelFrame(TunnelFrame.OPEN_ERROR, 1, byteArrayOf(0xff.toByte())).encode()
            )
        }
    }

    @Test
    fun `credit compares numeric versions and fails closed`() {
        listOf("10.4", "10.10", "11.0").forEach { assertTrue(TunnelFrame.creditSupported(it)) }
        listOf(null, "10.3", "9.99", "10", "10.4.0", "10.-4", "x.y").forEach {
            assertFalse(TunnelFrame.creditSupported(it))
        }
    }

    @Test
    fun `URL policy only forwards explicit loopback names`() {
        listOf("daemon.localhost", "localhost", "127.0.0.1", "[::1]", "0.0.0.0").forEach {
            assertEquals(8080, PreviewUrl.remotePort(PreviewUrl.parse("http://$it:8080/a")))
        }
        assertNull(PreviewUrl.remotePort(PreviewUrl.parse("https://example.com/")))
        assertNull(PreviewUrl.remotePort(PreviewUrl.parse("http://127.0.0.1:8080"), setOf(8080)))
        assertEquals(
            8080,
            PreviewUrl.remotePort(PreviewUrl.parse("http://daemon.localhost:8080"), setOf(8080))
        )
        listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "http://u:p@localhost/",
            "http://localhost:0",
            "http://localhost:65536"
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { PreviewUrl.parse(it) }
        }
    }
}
