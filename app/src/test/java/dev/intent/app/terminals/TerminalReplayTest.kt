package dev.intent.app.terminals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalReplayTest {
    @Test
    fun unsignedOffsetsPreserveFullWirePrecision() {
        val replay = TerminalReplay()
        replay.reset("boot")
        val result = replay.accept(
            TerminalRange("YQ==", "boot", "18446744073709551614", "18446744073709551615"),
            true
        )
        assertEquals("a", result.bytes.toString(Charsets.UTF_8))
        assertEquals(ULong.MAX_VALUE, replay.next)
        assertTrue(result.truncated)
        assertTrue(
            replay.accept(
                TerminalRange("YQ==", "boot", "18446744073709551614", "18446744073709551615"),
                false
            ).bytes.isEmpty()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun partialCursorExtensionIsRejected() {
        TerminalReplay().apply {
            reset("boot")
        }.accept(TerminalRange("YQ==", "boot", null, null), true)
    }
}
