package dev.intent.tailcat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TailcatTest {
    @Test
    fun buildWithoutArchiveUsesUnavailableFactory() {
        assertFalse(Tailcat.available)
        assertThrows(TunnelUnavailableException::class.java) { Tailcat.createClient("test-token") }
    }
}
