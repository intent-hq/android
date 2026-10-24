package dev.intent.tailcat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class UnavailableTunnelClientFactoryTest {
    @Test
    fun unavailableFactoryRejectsTunnelCreation() {
        assertFalse(UnavailableTunnelClientFactory.available)
        assertThrows(TunnelUnavailableException::class.java) {
            UnavailableTunnelClientFactory.createClient("test-token")
        }
    }
}
