package dev.intent.app.scripts

import dev.intent.core.protocol.models.WorkspaceScriptActionResult
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceScriptsStoreTest {
    @Test
    fun `transport loss without subscription error disables actions and fences pending work`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.setRunning("script", true)
        store.refresh()
        val staleRead = session.reads.last()
        store.connectionChanged(false)
        assertFalse(store.state.value.connected)
        assertTrue(store.state.value.pending.isEmpty())
        staleRead.complete(listOf(script("script", "running")))
        assertEquals("idle", store.state.value.scripts.single().runtime?.status)
        store.setRunning("script", true)
        assertEquals(1, session.actions.size)
        store.connectionChanged(true)
        assertFalse(store.state.value.connected)
        session.ready()
        session.reads.last().complete(listOf(script("script", "running")))
        session.action.completeExceptionally(IllegalStateException("Old transport lost"))
        assertTrue(store.state.value.connected)
        assertEquals("running", store.state.value.scripts.single().runtime?.status)
        assertEquals(null, store.state.value.error)
        assertEquals(1, session.actions.size)
    }

    @Test
    fun `subscription admission before route readiness waits then reads fresh state`() {
        val session = FakeScripts()
        val store = WorkspaceScriptsStore(session, "workspace")
        store.start(ready = false)
        session.ready()
        assertFalse(store.state.value.connected)
        assertTrue(session.reads.isEmpty())
        store.connectionChanged(true)
        assertEquals(1, session.reads.size)
        session.reads.single().complete(listOf(script("script")))
        assertTrue(store.state.value.connected)
        store.connectionChanged(true)
        assertEquals(1, session.reads.size)
    }

    @Test
    fun `list rejects another workspace and event invalidation discards in-flight response`() {
        val session = FakeScripts()
        val store = WorkspaceScriptsStore(session, "workspace")
        store.start()
        session.ready()
        session.changed()
        session.reads[0].complete(listOf(script("old")))
        assertTrue(store.state.value.scripts.isEmpty())
        assertEquals(2, session.reads.size)
        session.reads[1].complete(
            listOf(script("fresh"), script("foreign").copy(workspaceId = "another"))
        )
        assertEquals(listOf("fresh"), store.state.value.scripts.map { it.id })
        assertFalse(store.state.value.loading)
    }

    @Test
    fun `start is not duplicated and status is refreshed after acknowledgement`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.setRunning("script", true)
        store.setRunning("script", true)
        assertEquals(listOf("script" to true), session.actions)
        assertEquals("idle", store.state.value.scripts.single().runtime?.status)
        session.action.complete(WorkspaceScriptActionResult(true, "script"))
        assertEquals(listOf("script"), session.statusReads)
        session.status.complete(WorkspaceScriptRuntime("starting"))
        session.reads.last().complete(listOf(script("script", "starting")))
        assertEquals("starting", store.state.value.scripts.single().runtime?.status)
        assertTrue(store.state.value.pending.isEmpty())
        store.setRunning("script", false)
        assertEquals("script" to false, session.actions.last())
    }

    @Test
    fun `disconnect drops stale action completion and never replays mutation`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.setRunning("script", true)
        session.failed(IllegalStateException())
        assertFalse(store.state.value.connected)
        session.ready()
        session.reads.last().complete(listOf(script("script", "running")))
        session.action.completeExceptionally(IllegalStateException())
        assertTrue(store.state.value.connected)
        assertEquals("running", store.state.value.scripts.single().runtime?.status)
        assertEquals(1, session.actions.size)
        assertEquals(null, store.state.value.error)
    }

    @Test
    fun `rejected action does not assume successful launch`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.setRunning("script", true)
        session.action.complete(WorkspaceScriptActionResult(false, "script"))
        assertNotNull(store.state.value.error)
        assertEquals("idle", store.state.value.scripts.single().runtime?.status)
        assertTrue(store.state.value.pending.isEmpty())
        assertTrue(session.statusReads.isEmpty())
    }

    @Test
    fun `closed store rejects late list and closes subscription`() {
        val session = FakeScripts()
        val store = WorkspaceScriptsStore(session, "workspace")
        store.start()
        session.ready()
        store.close()
        session.reads.single().complete(listOf(script("late")))
        assertTrue(store.state.value.scripts.isEmpty())
        assertTrue(session.closed)
    }

    private fun readyStore(session: FakeScripts): WorkspaceScriptsStore {
        val store = WorkspaceScriptsStore(session, "workspace")
        store.start()
        session.ready()
        session.reads.single().complete(listOf(script("script")))
        return store
    }

    private fun script(id: String, status: String = "idle") = WorkspaceScriptResource(
        id,
        "workspace",
        "Build",
        "./build",
        "command",
        WorkspaceScriptRuntime(status)
    )

    private class FakeScripts : WorkspaceScriptSession {
        val reads = mutableListOf<CompletableFuture<List<WorkspaceScriptResource>>>()
        val actions = mutableListOf<Pair<String, Boolean>>()
        val action = CompletableFuture<WorkspaceScriptActionResult>()
        val statusReads = mutableListOf<String>()
        val status = CompletableFuture<WorkspaceScriptRuntime>()
        var closed = false
        lateinit var changed: () -> Unit
        lateinit var ready: () -> Unit
        lateinit var failed: (Throwable) -> Unit
        override fun list() = CompletableFuture<List<WorkspaceScriptResource>>().also {
            reads.add(it)
        }
        override fun status(id: String): CompletableFuture<WorkspaceScriptRuntime> {
            statusReads.add(id)
            return status
        }
        override fun setRunning(
            id: String,
            running: Boolean
        ): CompletableFuture<WorkspaceScriptActionResult> {
            actions.add(id to running)
            return action
        }
        override fun subscribe(
            changed: () -> Unit,
            ready: () -> Unit,
            failed: (Throwable) -> Unit
        ): Closeable {
            this.changed = changed
            this.ready = ready
            this.failed = failed
            return Closeable { closed = true }
        }
    }
}
