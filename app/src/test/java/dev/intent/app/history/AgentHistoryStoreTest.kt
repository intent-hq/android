package dev.intent.app.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHistoryStoreTest {
    @Test
    fun revisitingMovesToFrontUpdatesNameAndKeepsKnownWorkspaceTitle() {
        val store = AgentHistoryStore()
        store.record(visit("one", title = "Workspace"))
        store.record(visit("two"))
        store.record(visit("one").copy(agentName = "Renamed", visitedAt = 3))
        assertEquals(listOf("one", "two"), store.entries.value.map { it.agentId })
        assertEquals("Workspace", store.entries.value.first().workspaceTitle)
        assertEquals("Renamed", store.entries.value.first().agentName)
        assertEquals(3L, store.entries.value.first().visitedAt)
    }

    @Test
    fun limitsAndRestoresHistory() {
        var persisted: String? = null
        val store = AgentHistoryStore(write = { persisted = it })
        repeat(35) { store.record(visit(it.toString())) }
        val restored = AgentHistoryStore(read = { persisted })
        assertEquals(30, restored.entries.value.size)
        assertEquals("34", restored.entries.value.first().agentId)
        assertEquals("5", restored.entries.value.last().agentId)
        assertEquals(store.entries.value, restored.entries.value)
    }

    @Test
    fun connectionsHaveSeparateIdentityAndWorkspaceRemoval() {
        val store = AgentHistoryStore()
        store.record(visit("one"))
        store.record(visit("one").copy(connectionId = "other"))
        assertEquals(2, store.entries.value.size)
        assertEquals(1, store.visible(setOf("connection")).size)
        store.removeWorkspace("other", "workspace")
        assertEquals("connection", store.entries.value.single().connectionId)
    }

    @Test
    fun clearIsPersistedAndCorruptHistoryIsIgnored() {
        var persisted: String? = null
        val store = AgentHistoryStore(write = { persisted = it })
        store.record(visit("one"))
        store.clear()
        assertTrue(AgentHistoryStore(read = { persisted }).entries.value.isEmpty())
        assertTrue(AgentHistoryStore(read = { "broken" }).entries.value.isEmpty())
    }

    private fun visit(id: String, title: String? = null) =
        AgentVisit("connection", "workspace", id, "Agent $id", title, 1)
}
