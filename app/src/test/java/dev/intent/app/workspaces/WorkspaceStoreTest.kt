package dev.intent.app.workspaces

import dev.intent.core.protocol.models.EntityDelta
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStoreTest {
    @Test
    fun initialLoadAndLiveChanges() {
        val session = FakeWorkspaceSession()
        val store = WorkspaceStore(session)
        store.start()
        session.admit()
        assertTrue(store.state.value.isLoading)
        session.reads.last().complete(listOf(Workspace("one", "First")))
        assertEquals("First", store.state.value.workspaces.single().title)
        session.push(
            SubscriptionPush(
                "lease",
                "workspace",
                1,
                delta = EntityDelta(
                    updated = listOf(Workspace("one", "Changed", status = "Archived"))
                )
            )
        )
        assertEquals("Archived", store.state.value.workspaces.single().status)
        session.push(
            SubscriptionPush(
                "lease",
                "workspace",
                2,
                delta = EntityDelta(removedIds = listOf("one"))
            )
        )
        assertTrue(store.state.value.workspaces.isEmpty())
    }

    @Test
    fun snapshotWinsOverOlderRead() {
        val session = FakeWorkspaceSession()
        val store = WorkspaceStore(session)
        store.start()
        session.admit()
        session.push(
            SubscriptionPush("lease", "workspace", 0, snapshot = listOf(Workspace("new", "Live")))
        )
        session.reads.last().complete(listOf(Workspace("old", "Stale")))
        assertEquals("new", store.state.value.workspaces.single().id)
    }

    @Test
    fun reconnectRefreshFencesPreviousRead() {
        val session = FakeWorkspaceSession()
        val store = WorkspaceStore(session)
        store.start()
        session.admit()
        session.admit()
        session.reads[1].complete(listOf(Workspace("new", "New connection")))
        session.reads[0].complete(listOf(Workspace("old", "Old connection")))
        assertEquals("new", store.state.value.workspaces.single().id)
    }

    @Test
    fun errorsKeepExistingRowsAndRetryClearsError() {
        val session = FakeWorkspaceSession()
        val store = WorkspaceStore(session)
        store.start()
        session.admit()
        session.reads.last().complete(listOf(Workspace("one", "First")))
        store.refresh()
        session.reads.last().completeExceptionally(IllegalStateException("Offline"))
        assertEquals("Offline", store.state.value.error)
        assertFalse(store.state.value.isLoading)
        assertEquals(1, store.state.value.workspaces.size)
        store.refresh()
        session.reads.last().complete(emptyList())
        assertEquals(null, store.state.value.error)
        assertTrue(store.state.value.workspaces.isEmpty())
    }

    @Test
    fun stopAndRestartIgnoreOldSubscriptionCallbacks() {
        val session = FakeWorkspaceSession()
        val store = WorkspaceStore(session)
        store.start()
        val oldPush = session.push
        val oldAdmit = session.admit
        store.stop()
        store.start()
        session.admit()
        session.reads.last().complete(listOf(Workspace("new", "Current")))
        oldAdmit()
        oldPush(SubscriptionPush("old", "workspace", 0, snapshot = emptyList()))
        assertEquals(1, session.reads.size)
        assertEquals("new", store.state.value.workspaces.single().id)
    }

    @Test
    fun closeReleasesSubscriptionAndIgnoresPendingRead() {
        val session = FakeWorkspaceSession()
        val store = WorkspaceStore(session)
        store.start()
        store.start()
        assertEquals(1, session.subscriptions)
        session.admit()
        store.close()
        session.reads.last().complete(listOf(Workspace("one", "First")))
        assertTrue(session.released)
        assertTrue(store.state.value.workspaces.isEmpty())
    }
}

private class FakeWorkspaceSession : WorkspaceSession {
    val reads = mutableListOf<CompletableFuture<List<Workspace>>>()
    lateinit var admit: () -> Unit
    lateinit var push: (SubscriptionPush<Workspace>) -> Unit
    var subscriptions = 0
    var released = false

    override fun list(): CompletableFuture<List<Workspace>> =
        CompletableFuture<List<Workspace>>().also(reads::add)

    override fun subscribe(
        onPush: (SubscriptionPush<Workspace>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        subscriptions++
        admit = onSubscribed
        push = onPush
        return Closeable { released = true }
    }
}
