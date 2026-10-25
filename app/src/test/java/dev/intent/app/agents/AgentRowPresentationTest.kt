package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentRowPresentationTest {
    @Test
    fun waitingReplacesIdleAndActiveLabels() {
        assertEquals(
            "Waiting for agents",
            Agent("a", "A", isWaitingForOtherAgents = true).rowStatus()
        )
        assertEquals(
            "Waiting for agents",
            Agent("a", "A", status = "active", waitingForAgentIds = listOf("child")).rowStatus()
        )
    }

    @Test
    fun activeFlagsAndTerminalStatesUseAvatarLabels() {
        assertEquals("Running", Agent("a", "A", isStreaming = true).rowStatus())
        assertEquals("Running", Agent("a", "A", status = "active").rowStatus())
        assertEquals("Completed", Agent("a", "A", status = "completed").rowStatus())
        assertEquals("Failed", Agent("a", "A", status = "error").rowStatus())
        assertEquals("Idle", Agent("a", "A").rowStatus())
    }

    @Test
    fun attentionTakesPriorityOverWaiting() {
        val agent = Agent(
            "a",
            "A",
            isWaitingForOtherAgents = true,
            metadata = JsonObject(mapOf("attentionRequestKind" to JsonPrimitive("blocker")))
        )
        assertEquals("Blocked", agent.rowStatus())
        assertEquals(
            "Question pending",
            agent.copy(
                metadata = JsonObject(mapOf("pendingQuestionsMessageId" to JsonPrimitive("q")))
            ).rowStatus()
        )
    }

    @Test
    fun previewUsesLatestRoleAndNormalizesLineBreaks() {
        val agent = Agent(
            "a",
            "A",
            digest = "  Summary\n\nwith\tspacing  ",
            lastAgentResponse = "Response",
            lastUserMessage = "User message"
        )
        assertEquals("Summary with spacing", agent.rowPreview())
        assertEquals("User message", agent.copy(lastMessageRole = "user").rowPreview())
        assertEquals("Response", agent.copy(digest = null).rowPreview())
        assertNull(agent.copy(digest = "  ").rowPreview())
        assertNull(Agent("a", "A").rowPreview())
    }
}
