package dev.intentapp.android.scripts

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
        store.launch("script")
        assertEquals("script", session.statusReads.single())
        store.refresh()
        val staleRead = session.reads.last()
        store.connectionChanged(false)
        assertFalse(store.state.value.connected)
        assertEquals(null, store.state.value.launching)
        staleRead.complete(listOf(script("script", "running")))
        assertEquals("idle", store.state.value.scripts.single().runtime?.status)
        store.launch("script")
        assertEquals(1, session.statusReads.size)
        store.connectionChanged(true)
        assertFalse(store.state.value.connected)
        session.ready()
        session.reads.last().complete(listOf(script("script", "running")))
        session.statuses.first().completeExceptionally(IllegalStateException("Old transport lost"))
        assertTrue(store.state.value.connected)
        assertEquals("running", store.state.value.scripts.single().runtime?.status)
        assertEquals(null, store.state.value.error)
        assertTrue(session.actions.isEmpty())
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
        assertEquals(1, session.discoveries)
        session.reads.single().complete(listOf(script("script")))
        assertTrue(store.state.value.connected)
        assertTrue(store.state.value.loaded)
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
    fun `launch starts an idle script once and confirms with a status read`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.launch("script")
        store.launch("script")
        assertEquals(listOf("script"), session.statusReads)
        session.statuses[0].complete(WorkspaceScriptRuntime("idle"))
        assertEquals(listOf("script" to true), session.actions)
        assertTrue(store.state.value.isLaunching(store.state.value.rows.single()))
        session.action.complete(WorkspaceScriptActionResult(true, "script"))
        session.statuses[1].complete(WorkspaceScriptRuntime("starting"))
        assertEquals("starting", store.state.value.scripts.single().runtime?.status)
        assertEquals("script", store.state.value.launching)
        session.reads.last().complete(listOf(script("script", "exited")))
        assertEquals(null, store.state.value.launching)
    }

    @Test
    fun `launch opens the detected address of a running service`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.launch("script")
        session.statuses[0].complete(
            WorkspaceScriptRuntime("running", detectedUrl = "http://localhost:5173")
        )
        assertTrue(session.actions.isEmpty())
        assertEquals("http://localhost:5173", store.state.value.openUrl)
        assertEquals(null, store.state.value.launching)
        store.consumeOpenUrl()
        assertEquals(null, store.state.value.openUrl)
    }

    @Test
    fun `manifest script is saved before its first launch`() {
        val session = FakeScripts()
        val store = readyStore(session, emptyList())
        session.discovery.complete(
            WorkspaceScriptDiscovery.scripts(
                "workspace",
                mapOf("package.json" to """{"scripts":{"dev":"vite"}}"""),
                setOf("package.json")
            )
        )
        val row = store.state.value.rows.single()
        assertFalse(row.registered)
        store.launch(row.id)
        session.reads.last().complete(emptyList())
        assertEquals(listOf(Triple("dev", "npm run dev", "service")), session.created)
        session.creation.complete(
            WorkspaceScriptResource("saved", "workspace", "dev", "npm run dev", "service")
        )
        assertEquals("saved", store.state.value.launching)
        assertEquals(listOf("saved"), store.state.value.rows.map { it.id })
        assertEquals(listOf("saved"), session.statusReads)
    }

    @Test
    fun `stop and remove confirm before changing rows`() {
        val session = FakeScripts()
        val store = readyStore(session, listOf(script("script", "running")))
        store.stop("script")
        assertTrue(store.state.value.isUpdating("script"))
        store.stop("script")
        assertEquals(listOf("script" to false), session.actions)
        session.action.complete(WorkspaceScriptActionResult(true, "script"))
        session.statuses.single().complete(WorkspaceScriptRuntime("idle"))
        assertEquals("idle", store.state.value.scripts.single().runtime?.status)
        assertFalse(store.state.value.isUpdating("script"))
        session.reads.last().complete(listOf(script("script")))
        store.remove("script")
        session.removal.complete(WorkspaceScriptActionResult(true, "script"))
        assertTrue(store.state.value.scripts.isEmpty())
    }

    @Test
    fun `rejected action reports an error and keeps the row`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.remove("script")
        session.removal.complete(WorkspaceScriptActionResult(false, "script"))
        assertNotNull(store.state.value.error)
        assertEquals(listOf("script"), store.state.value.scripts.map { it.id })
        assertTrue(store.state.value.pending.isEmpty())
    }

    @Test
    fun `output loads, refreshes on script events and ignores stale reads`() {
        val session = FakeScripts()
        val store = readyStore(session)
        store.showOutput("script")
        assertTrue(store.state.value.output?.loading == true)
        session.changed()
        session.outputs[0].complete("stale")
        assertEquals(null, store.state.value.output?.text)
        session.outputs[1].complete("\u001B[32mok\u001B[0m\nprogress 1\rprogress 2\n")
        assertEquals("ok\nprogress 2", store.state.value.output?.text)
        store.hideOutput()
        assertEquals(null, store.state.value.output)
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

    private fun readyStore(
        session: FakeScripts,
        rows: List<WorkspaceScriptResource> = listOf(script("script"))
    ): WorkspaceScriptsStore {
        val store = WorkspaceScriptsStore(session, "workspace")
        store.start()
        session.ready()
        session.reads.single().complete(rows)
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
        val removal = CompletableFuture<WorkspaceScriptActionResult>()
        val statusReads = mutableListOf<String>()
        val statuses = mutableListOf<CompletableFuture<WorkspaceScriptRuntime>>()
        val outputs = mutableListOf<CompletableFuture<String>>()
        val created = mutableListOf<Triple<String, String, String>>()
        val creation = CompletableFuture<WorkspaceScriptResource>()
        val discovery = CompletableFuture<List<WorkspaceScriptRow>>()
        var discoveries = 0
        var closed = false
        lateinit var changed: () -> Unit
        lateinit var ready: () -> Unit
        lateinit var failed: (Throwable) -> Unit
        override fun list() = CompletableFuture<List<WorkspaceScriptResource>>().also {
            reads.add(it)
        }
        override fun status(id: String): CompletableFuture<WorkspaceScriptRuntime> {
            statusReads.add(id)
            return CompletableFuture<WorkspaceScriptRuntime>().also { statuses.add(it) }
        }
        override fun setRunning(
            id: String,
            running: Boolean
        ): CompletableFuture<WorkspaceScriptActionResult> {
            actions.add(id to running)
            return action
        }
        override fun create(name: String, command: String, mode: String) =
            creation.also { created.add(Triple(name, command, mode)) }
        override fun remove(id: String) = removal
        override fun output(id: String) = CompletableFuture<String>().also { outputs.add(it) }
        override fun discover() = discovery.also { discoveries++ }
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
