package dev.intent.core.protocol.session

import dev.intent.core.protocol.reconnectDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryPolicyTest {
    @Test
    fun `short connections keep backoff and stable connections reset it`() {
        assertEquals(0L, recoveryDelayMillis(0))
        assertEquals(1000L, recoveryDelayMillis(1))
        assertEquals(2000L, recoveryDelayMillis(2))
        assertEquals(4, recoveryAttempt(100, 4))
        assertEquals(1, recoveryAttempt(0, 0))
        assertEquals(0, recoveryAttempt(10_000, 4))
        assertEquals(4, recoveryAttempt(null, 4))
        assertEquals(30_000L, reconnectDelayMillis(100))
    }
}
