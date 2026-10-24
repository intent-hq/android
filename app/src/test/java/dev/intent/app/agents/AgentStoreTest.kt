package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.EntityDelta
import dev.intent.core.protocol.models.SubscriptionPush
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStoreTest {
    @Test
    fun scopeAndRetirementFilterLiveRows() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        session.admit()
        session.reads.last().complete(
            listOf(
                Agent("local", "Local", workspaceId = "workspace"),
                Agent("foreign", "Foreign", workspaceId = "other"),
                Agent("retired", "Retired", retiredAt = "2026-10-24"),
                Agent("deleted", "Deleted", status = "deleted")
            )
        )
        assertEquals(listOf("local"), store.state.value.agents.map { it.id })
        session.push(
            SubscriptionPush(
                "lease",
                "delta",
                1,
                delta = EntityDelta(updated = listOf(Agent("local", "Local", retiredAt = "now")))
            )
        )
        assertTrue(store.state.value.agents.isEmpty())
    }

    @Test
    fun initialLoadAndLiveChanges() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        session.admit()
        assertTrue(store.state.value.isLoading)
        session.reads.last().complete(listOf(Agent("one", "First")))
        assertEquals("First", store.state.value.agents.single().name)
        session.push(
            SubscriptionPush(
                "lease",
                "agent",
                1,
                delta = EntityDelta(
                    updated = listOf(Agent("one", "Changed", status = "active"))
                )
            )
        )
        assertEquals("active", store.state.value.agents.single().status)
        session.push(
            SubscriptionPush(
                "lease",
                "agent",
                2,
                delta = EntityDelta(removedIds = listOf("one"))
            )
        )
        assertTrue(store.state.value.agents.isEmpty())
    }

    @Test
    fun snapshotWinsOverOlderRead() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        session.admit()
        session.push(
            SubscriptionPush("lease", "agent", 0, snapshot = listOf(Agent("new", "Live")))
        )
        session.reads.last().complete(listOf(Agent("old", "Stale")))
        assertEquals("new", store.state.value.agents.single().id)
    }

    @Test
    fun reconnectRefreshFencesPreviousRead() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        session.admit()
        session.admit()
        session.reads[1].complete(listOf(Agent("new", "New connection")))
        session.reads[0].complete(listOf(Agent("old", "Old connection")))
        assertEquals("new", store.state.value.agents.single().id)
    }

    @Test
    fun errorsKeepExistingRowsAndRetryClearsError() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        session.admit()
        session.reads.last().complete(listOf(Agent("one", "First")))
        store.refresh()
        session.reads.last().completeExceptionally(IllegalStateException("Offline"))
        assertEquals("Offline", store.state.value.error)
        assertFalse(store.state.value.isLoading)
        assertEquals(1, store.state.value.agents.size)
        store.refresh()
        session.reads.last().complete(emptyList())
        assertEquals(null, store.state.value.error)
        assertTrue(store.state.value.agents.isEmpty())
    }

    @Test
    fun stopAndRestartIgnoreOldSubscriptionCallbacks() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        val oldPush = session.push
        val oldAdmit = session.admit
        store.stop()
        store.start()
        session.admit()
        session.reads.last().complete(listOf(Agent("new", "Current")))
        oldAdmit()
        oldPush(SubscriptionPush("old", "agent", 0, snapshot = emptyList()))
        assertEquals(1, session.reads.size)
        assertEquals("new", store.state.value.agents.single().id)
    }

    @Test
    fun closeReleasesSubscriptionAndIgnoresPendingRead() {
        val session = FakeAgentSession()
        val store = AgentStore(session, "workspace")
        store.start()
        store.start()
        assertEquals(1, session.subscriptions)
        session.admit()
        store.close()
        session.reads.last().complete(listOf(Agent("one", "First")))
        assertTrue(session.released)
        assertTrue(store.state.value.agents.isEmpty())
    }
}

private class FakeAgentSession : AgentSession {
    val reads = mutableListOf<CompletableFuture<List<Agent>>>()
    lateinit var admit: () -> Unit
    lateinit var push: (SubscriptionPush<Agent>) -> Unit
    var subscriptions = 0
    var released = false

    override fun list(): CompletableFuture<List<Agent>> =
        CompletableFuture<List<Agent>>().also(reads::add)

    override fun subscribe(
        onPush: (SubscriptionPush<Agent>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        subscriptions++
        admit = onSubscribed
        push = onPush
        return Closeable { released = true }
    }
}
