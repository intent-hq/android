package dev.intent.app.history

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentHistoryIdentityTest {
    private val visit = AgentVisit("one", "workspace", "agent", "Implementor", "Saved title")

    @Test
    fun activeConnectionUsesLiveWorkspaceTitleAndKeepsAgentInMetadata() {
        assertEquals(
            AgentHistoryIdentity("Renamed workspace", "Studio Mac · Implementor"),
            visit.historyIdentity("one", mapOf("workspace" to "Renamed workspace"), "Studio Mac")
        )
    }

    @Test
    fun anotherConnectionCannotSupplyTheTitleForTheSameWorkspaceId() {
        assertEquals(
            AgentHistoryIdentity("Saved title", "Laptop · Implementor"),
            visit.historyIdentity("other", mapOf("workspace" to "Other computer"), "Laptop")
        )
    }

    @Test
    fun blankLiveTitleKeepsSavedTitleAndMissingWorkspaceUsesAgentNameOnce() {
        assertEquals(
            "Saved title",
            visit.historyIdentity("one", mapOf("workspace" to " "), null).title
        )
        assertEquals(
            AgentHistoryIdentity("Implementor", "Laptop"),
            visit.copy(workspaceTitle = " ").historyIdentity(null, emptyMap(), "Laptop")
        )
    }
}
