package dev.intent.app.terminals

import dev.intent.core.protocol.models.TerminalAck
import dev.intent.core.protocol.models.TerminalBuffer
import dev.intent.core.protocol.models.TerminalCreated
import dev.intent.core.protocol.models.TerminalData
import dev.intent.core.protocol.models.TerminalInfo
import dev.intent.core.protocol.models.TerminalListResult
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLifecycleTest {
    @Test fun createValidatesOnceAndOpensNewShell() {
        val source = LifecycleSource()
        val store = started(source)
        store.create()
        store.create()
        assertTrue(store.state.value.creating)
        assertEquals(2, source.lists.size)
        source.lists.last().complete(list())
        assertEquals(1, source.creates.size)
        source.creates.single().complete(TerminalCreated("new"))
        assertFalse(store.state.value.creating)
        assertEquals("new", store.state.value.selected)
        assertEquals(listOf("new"), source.buffers)
        assertTrue(store.state.value.ready)
    }

    @Test fun createFailureOrInvalidReplyKeepsListAndAllowsExplicitRetry() {
        val source = LifecycleSource()
        val store = started(source)
        store.create()
        source.lists.last().completeExceptionally(IllegalStateException())
        assertTrue(source.creates.isEmpty())
        assertNotNull(store.state.value.actionError)
        assertFalse(store.state.value.creating)
        store.create()
        source.lists.last().complete(list())
        source.creates.last().complete(TerminalCreated(""))
        assertNull(store.state.value.selected)
        assertEquals(list().terminals, store.state.value.terminals)
        assertNotNull(store.state.value.actionError)
    }

    @Test fun deleteValidatesGuardsDuplicatesAndRemovesOnlyAfterSuccess() {
        val source = LifecycleSource()
        val store = started(source)
        store.select("old")
        store.delete("old")
        store.delete("old")
        store.create()
        assertEquals("old", store.state.value.deleting)
        source.lists.last().complete(list())
        assertEquals(listOf("old"), source.kills.map { it.first })
        assertEquals(1, store.state.value.terminals.size)
        source.kills.single().second.complete(TerminalAck(true))
        source.lists.last().complete(list(emptyList()))
        assertTrue(store.state.value.terminals.isEmpty())
        assertNull(store.state.value.selected)
        assertNull(store.state.value.deleting)
    }

    @Test fun failedDeleteKeepsTerminalAndRetryCanSucceed() {
        val source = LifecycleSource()
        val store = started(source)
        store.delete("old")
        source.lists.last().complete(list())
        source.kills.single().second.complete(TerminalAck(false))
        assertEquals(list().terminals, store.state.value.terminals)
        assertNotNull(store.state.value.actionError)
        assertNull(store.state.value.deleting)
        store.delete("old")
        source.lists.last().complete(list())
        source.kills.last().second.completeExceptionally(IllegalStateException())
        assertEquals(2, source.kills.size)
        assertNotNull(store.state.value.actionError)
    }

    @Test fun endedOrDifferentBootTerminalIsRemovedWithoutKill() {
        listOf(list(emptyList()), list().copy(daemonBootId = "new-boot")).forEach { result ->
            val source = LifecycleSource()
            val store = started(source)
            store.delete("old")
            source.lists.last().complete(result)
            assertTrue(source.kills.isEmpty())
            assertTrue(store.state.value.terminals.isEmpty())
        }
    }

    @Test fun reconnectDoesNotReplayMutationOrOpenLateCreation() {
        val source = LifecycleSource()
        val store = started(source)
        store.create()
        source.lists.last().complete(list())
        source.subscribed()
        source.lists.last().complete(list())
        source.creates.single().complete(TerminalCreated("new"))
        source.lists.last().complete(list(listOf(TerminalInfo("old"), TerminalInfo("new"))))
        assertEquals(1, source.creates.size)
        assertNull(store.state.value.selected)
        assertEquals(2, store.state.value.terminals.size)
        assertFalse(store.state.value.creating)
        store.create()
        source.subscribed()
        source.lists[source.lists.lastIndex - 1].complete(list())
        assertEquals(1, source.creates.size)
    }

    @Test fun closedAndDisconnectedStoresRejectMutationsAndLateReplies() {
        val source = LifecycleSource()
        val store = TerminalStore(source)
        store.create()
        store.delete("old")
        assertTrue(source.lists.isEmpty())
        store.start()
        source.subscribed()
        source.lists.last().complete(list())
        store.create()
        source.lists.last().complete(list())
        store.close()
        val before = store.state.value
        source.creates.single().complete(TerminalCreated("late"))
        store.delete("old")
        assertEquals(before, store.state.value)
        assertTrue(source.kills.isEmpty())
    }

    private fun started(source: LifecycleSource) = TerminalStore(source).also {
        it.start()
        source.subscribed()
        source.lists.last().complete(list())
    }
    private fun list(terminals: List<TerminalInfo> = listOf(TerminalInfo("old"))) =
        TerminalListResult(terminals, "boot")
}

private class LifecycleSource : TerminalSession {
    val lists = mutableListOf<CompletableFuture<TerminalListResult>>()
    val creates = mutableListOf<CompletableFuture<TerminalCreated>>()
    val kills = mutableListOf<Pair<String, CompletableFuture<TerminalAck>>>()
    val buffers = mutableListOf<String>()
    lateinit var subscribed: () -> Unit
    override fun list() = CompletableFuture<TerminalListResult>().also(lists::add)
    override fun create() = CompletableFuture<TerminalCreated>().also(creates::add)
    override fun kill(id: String) = CompletableFuture<TerminalAck>().also { kills.add(id to it) }
    override fun buffer(id: String): CompletableFuture<TerminalBuffer> {
        buffers.add(id)
        return CompletableFuture.completedFuture(TerminalBuffer(id, "", "boot", "0", "0"))
    }
    override fun write(id: String, bytes: ByteArray) =
        CompletableFuture.completedFuture(TerminalAck(true))
    override fun resize(id: String, columns: Int, rows: Int) =
        CompletableFuture.completedFuture(TerminalAck(true))
    override fun subscribe(
        onData: (TerminalData) -> Unit,
        onExit: (String) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        subscribed = onSubscribed
        return Closeable {}
    }
}
