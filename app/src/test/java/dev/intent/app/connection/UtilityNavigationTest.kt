package dev.intent.app.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilityNavigationTest {
    @Test
    fun pairingBackReturnsThroughConnectionsAndSettingsToTheCaller() {
        var stack = emptyList<String>().openUtility("Settings")
            .openUtility("Connections").openUtility("Pair connection")
        assertEquals("Pair connection", stack.last())
        stack = stack.dropLast(1)
        assertEquals("Connections", stack.last())
        stack = stack.dropLast(1)
        assertEquals("Settings", stack.last())
        assertEquals(emptyList<String>(), stack.dropLast(1))
    }

    @Test
    fun directConnectionsAndRepeatedMenuActionsDoNotAddFalseCallers() {
        val stack = emptyList<String>().openUtility("Connections")
        assertEquals(stack, stack.openUtility("Connections"))
        assertEquals(stack, stack.openUtility("Settings").openUtility("Connections"))
        assertEquals(emptyList<String>(), stack.dropLast(1))
    }
}
