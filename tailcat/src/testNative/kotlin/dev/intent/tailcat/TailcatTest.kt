package dev.intent.tailcat

import org.junit.Assert.assertTrue
import org.junit.Test

class TailcatTest {
    @Test
    fun buildWithArchiveSelectsNativeFactoryWithoutLoadingJni() {
        assertTrue(Tailcat.available)
    }
}
