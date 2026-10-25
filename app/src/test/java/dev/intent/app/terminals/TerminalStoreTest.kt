package dev.intent.app.terminals

import dev.intent.core.protocol.models.TerminalAck
import dev.intent.core.protocol.models.TerminalBuffer
import dev.intent.core.protocol.models.TerminalData
import dev.intent.core.protocol.models.TerminalInfo
import dev.intent.core.protocol.models.TerminalListResult
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalStoreTest {
    @Test
    fun snapshotAndDelayedEventsAppendOnlyUnseenBytes() {
        val source = FakeTerminals()
        val store = selected(source)
        source.data(data("abc", 0))
        source.buffers.last().complete(buffer("abc", 0))
        source.data(data("abc", 0))
        source.data(data("cde", 2))
        source.data(data("de", 5))
        assertEquals("abcdede", text(store))
        assertTrue(store.state.value.ready)
    }

    @Test
    fun reconnectReattachesWithoutReplayingInput() {
        val source = FakeTerminals()
        val store = ready(source)
        store.send("ls\r")
        assertEquals("ls\r", source.writes.single())
        source.subscribed()
        source.lists.last().complete(list())
        source.buffers.last().complete(buffer("abcdef", 0))
        source.writeResults.single().complete(TerminalAck(true))
        assertEquals("abcdef", text(store))
        assertEquals(1, source.writes.size)
        assertFalse(store.state.value.sending)
    }

    @Test
    fun gapFetchesUncappedSnapshotAndMarksEvictedOutput() {
        val source = FakeTerminals()
        val store = ready(source)
        source.data(data("xyz", 9))
        assertFalse(store.state.value.ready)
        source.buffers.last().complete(buffer("xyz", 9))
        assertEquals("xyz", text(store))
        assertEquals("Earlier output is no longer available.", store.state.value.notice)
    }

    @Test
    fun changedBootResetsCursorAndIgnoresStaleOutput() {
        val source = FakeTerminals()
        val store = ready(source)
        source.subscribed()
        source.lists.last().complete(list().copy(daemonBootId = "new"))
        source.buffers.last().complete(buffer("new", 0).copy(daemonBootId = "new"))
        source.data(data("stale", 3))
        assertEquals("new", text(store))
    }

    @Test
    fun resizeAndInputFailuresDoNotRetryWrites() {
        val source = FakeTerminals()
        val store = ready(source)
        store.resize(100, 30)
        assertEquals(100 to 30, source.sizes.last())
        store.send("x")
        store.send("y")
        assertEquals(listOf("x"), source.writes)
        source.writeResults.single().completeExceptionally(IllegalStateException())
        assertFalse(store.state.value.ready)
        store.send("z")
        assertEquals(listOf("x"), source.writes)
    }

    @Test
    fun selectionAndCloseFenceOldReads() {
        val source = FakeTerminals()
        val store = selected(source)
        val stale = source.buffers.last()
        store.select(null)
        stale.complete(buffer("stale", 0))
        assertEquals(null, store.state.value.selected)
        assertEquals("", text(store))
        store.select("term")
        store.close()
        val before = store.state.value
        source.buffers.last().complete(buffer("closed", 0))
        source.data(data("closed", 0))
        assertEquals(before, store.state.value)
        assertTrue(source.closed)
    }

    @Test
    fun exitReconcilesFinalBytesAndDisablesInput() {
        val source = FakeTerminals()
        val store = ready(source)
        source.exit("term")
        source.buffers.last().complete(buffer("abcfinal", 0))
        assertEquals("abcfinal", text(store))
        assertTrue(store.state.value.ended)
        assertFalse(store.state.value.ready)
        store.send("x")
        assertTrue(source.writes.isEmpty())
    }

    @Test
    fun missingTerminalAndLegacyReplayHaveExplicitStates() {
        val source = FakeTerminals()
        val store = selected(source)
        source.data(TerminalData("term", encoded("abc")))
        source.buffers.last().complete(TerminalBuffer("term", encoded("abc")))
        assertEquals("abc", text(store))
        assertTrue(store.state.value.notice!!.contains("output cursors"))
        source.subscribed()
        source.lists.last().complete(TerminalListResult(emptyList(), "boot"))
        assertEquals(null, store.state.value.selected)
        assertFalse(store.state.value.ready)
    }

    @Test
    fun invalidRangeStopsOutputAndUnrecoverableGapDoesNotLoop() {
        val source = FakeTerminals()
        val store = ready(source)
        source.data(data("abc", 9))
        source.buffers.last().complete(buffer("abc", 0))
        assertEquals(2, source.buffers.size)
        assertFalse(store.state.value.ready)
        store.refresh()
        source.lists.last().complete(list())
        source.buffers.last().complete(buffer("abc", 0).copy(endOffset = "8"))
        assertFalse(store.state.value.ready)
        assertTrue(store.state.value.error != null)
    }

    private fun selected(source: FakeTerminals): TerminalStore = TerminalStore(source).also {
        it.start()
        source.subscribed()
        source.lists.last().complete(list())
        it.select("term")
    }
    private fun ready(source: FakeTerminals): TerminalStore = selected(source).also {
        source.buffers.last().complete(buffer("abc", 0))
    }
    private fun text(store: TerminalStore) = store.state.value.frame.lines.firstOrNull()
        ?.joinToString("") { it.text.toString() }?.trimEnd().orEmpty()
}

private fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
private fun list() = TerminalListResult(listOf(TerminalInfo("term")), "boot")
private fun data(text: String, start: Int) = TerminalData(
    "term",
    encoded(text),
    "boot",
    start.toString(),
    (start + text.toByteArray().size).toString()
)
private fun buffer(text: String, start: Int) = TerminalBuffer(
    "term",
    encoded(text),
    "boot",
    start.toString(),
    (start + text.toByteArray().size).toString()
)

private class FakeTerminals : TerminalSession {
    val lists = mutableListOf<CompletableFuture<TerminalListResult>>()
    val buffers = mutableListOf<CompletableFuture<TerminalBuffer>>()
    val writes = mutableListOf<String>()
    val writeResults = mutableListOf<CompletableFuture<TerminalAck>>()
    val sizes = mutableListOf<Pair<Int, Int>>()
    lateinit var data: (TerminalData) -> Unit
    lateinit var exit: (String) -> Unit
    lateinit var subscribed: () -> Unit
    var closed = false
    override fun list() = CompletableFuture<TerminalListResult>().also(lists::add)
    override fun buffer(id: String) = CompletableFuture<TerminalBuffer>().also(buffers::add)
    override fun write(id: String, bytes: ByteArray): CompletableFuture<TerminalAck> {
        writes += bytes.toString(Charsets.UTF_8)
        return CompletableFuture<TerminalAck>().also(writeResults::add)
    }
    override fun resize(id: String, columns: Int, rows: Int): CompletableFuture<TerminalAck> {
        sizes += columns to rows
        return CompletableFuture.completedFuture(TerminalAck(true))
    }
    override fun subscribe(
        onData: (TerminalData) -> Unit,
        onExit: (String) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        data = onData
        exit = onExit
        subscribed = onSubscribed
        return Closeable { closed = true }
    }
}
