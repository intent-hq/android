package dev.intent.app.assistant

import dev.intent.core.protocol.models.Agent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantHistoryTimestampTest {
    @Test
    fun historyPrefersLastActivityAndFallsBackToValidEarlierMetadata() {
        val timestamp = "2026-11-04T12:00:00Z"
        val agent = Agent(
            id = "thread",
            name = "Assistant",
            lastActivity = timestamp,
            updatedAt = "2026-11-03T12:00:00Z"
        )
        assertEquals(Instant.parse(timestamp).toEpochMilli(), assistantHistoryTimestamp(agent))
        assertEquals(
            Instant.parse(agent.updatedAt).toEpochMilli(),
            assistantHistoryTimestamp(agent.copy(lastActivity = "invalid"))
        )
        assertEquals(
            Instant.parse(timestamp).toEpochMilli(),
            assistantHistoryTimestamp(Agent(id = "thread", name = "Assistant", createdAt = timestamp))
        )
    }

    @Test
    fun missingOrInvalidMetadataDoesNotInventATime() {
        assertNull(assistantHistoryTimestamp(Agent(id = "thread", name = "Assistant")))
        assertNull(
            assistantHistoryTimestamp(Agent(id = "thread", name = "Assistant", lastActivity = "invalid"))
        )
    }
}
