package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentListFilterTest {
    private val agents = listOf(
        Agent("root", "Coordinator"),
        Agent("done", "Old work", parentAgentId = "root", status = "completed"),
        Agent("active", "Fix search", parentAgentId = "done", isResponding = true),
        Agent("idle", "Previous search", parentAgentId = "root"),
        Agent("waiting", "Waiting", parentAgentId = "root", isWaitingForOtherAgents = true)
    )

    @Test
    fun `current retains active and waiting branches with idle ancestors`() {
        assertEquals(
            listOf("root", "done", "active", "waiting"),
            filterAgents(agents, "", true).map { it.id }
        )
    }

    @Test
    fun `search combines current filter and retains hierarchy`() {
        val rows = AgentTree(filterAgents(agents, " SEARCH ", true)).rows()
        assertEquals(listOf("root", "done", "active"), rows.map { it.agent.id })
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
        assertEquals(listOf(1, 1, 0), rows.map { it.childCount })
        assertEquals(agents, filterAgents(agents, "", false))
        assertEquals(emptyList<Agent>(), filterAgents(agents, "missing", false))
    }

    @Test
    fun `attention and failed agents remain current but settled agents do not`() {
        val rows = listOf(
            Agent(
                "blocked",
                "Blocked",
                metadata = buildJsonObject {
                    put("attentionRequestKind", "blocker")
                }
            ),
            Agent(
                "question",
                "Question",
                metadata = buildJsonObject {
                    put("pendingQuestionsMessageId", "q")
                }
            ),
            Agent("failed", "Failed", status = "failed"),
            Agent("stream", "Streaming", isStreaming = true),
            Agent("complete", "Complete", status = "complete")
        )
        assertEquals(rows.dropLast(1), filterAgents(rows, "", true))
    }

    @Test
    fun `cycles and orphan matches remain finite and reachable`() {
        val rows = listOf(
            Agent("a", "A", parentAgentId = "b"),
            Agent("b", "Match", parentAgentId = "a"),
            Agent("orphan", "Match", parentAgentId = "missing")
        )
        assertEquals(3, AgentTree(filterAgents(rows, "match", false)).rows().size)
    }
}
