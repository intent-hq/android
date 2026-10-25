package dev.intent.app.notes

import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteMetadata
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.TaskMetadata
import dev.intent.core.protocol.models.TaskUpdateNoteStatusResult
import dev.intent.core.protocol.models.TaskUpdateResult
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMutationStoreTest {
    @Test
    fun `checkbox is optimistic and rolls back after a failed write`() {
        val source = MutationSource()
        val store = loaded(source)
        store.updateLine(store.state.tasks.single(), "done")
        assertEquals("done", store.state.tasks.single().status)
        assertTrue(store.state.saving)
        assertEquals("Work", source.expected)
        source.lineWrite.completeExceptionally(IllegalStateException("offline"))
        assertEquals("todo", store.state.tasks.single().status)
        assertFalse(store.state.saving)
        assertNotNull(store.state.mutationError)
    }

    @Test
    fun `write acknowledgment refetches linked task authoritative status`() {
        val source = MutationSource()
        val store = loaded(source)
        store.updateLine(store.state.tasks.single(), "done")
        source.event(NoteEvent("task:status-changed", "linked"))
        assertEquals(1, source.reads.size)
        source.lineWrite.complete(TaskUpdateResult(true, "note", 1, "Work", "Work", "done"))
        source.complete("done", "complete")
        assertEquals("done", store.state.tasks.single().status)
        assertEquals("complete", store.state.note?.metadata?.task?.status)
        assertFalse(store.state.saving)
    }

    @Test
    fun `task note uses revision and honors authoritative advisory response`() {
        val source = MutationSource()
        val store = loaded(source)
        store.updateStatus("complete")
        assertEquals("complete", store.state.note?.metadata?.task?.status)
        assertEquals(7L, source.expectedVersion)
        val unchanged = source.fixture("todo", "not_started")
        source.statusWrite.complete(
            TaskUpdateNoteStatusResult(true, "note", "not_started", unchanged, "unchanged")
        )
        assertEquals("not_started", store.state.note?.metadata?.task?.status)
        source.complete("todo", "not_started")
        assertFalse(store.state.loading)
    }

    @Test
    fun `live events refresh status without a local write`() {
        val source = MutationSource()
        val store = loaded(source)
        source.event(NoteEvent("task:status-changed", "note"))
        source.complete("in-progress", "in_progress")
        assertEquals("in-progress", store.state.tasks.single().status)
        assertEquals("in_progress", store.state.note?.metadata?.task?.status)
    }

    @Test
    fun `pending write fences older reads and prevents duplicate writes`() {
        val source = MutationSource()
        val store = loaded(source)
        store.refresh()
        store.updateStatus("complete")
        store.updateStatus("blocked")
        source.complete("todo", "waiting")
        assertEquals("complete", store.state.note?.metadata?.task?.status)
        assertEquals(1, source.statusCalls)
        source.statusWrite.completeExceptionally(IllegalStateException("conflict"))
        assertEquals("not_started", store.state.note?.metadata?.task?.status)
    }

    @Test
    fun `deleted note rejects a late mutation reply`() {
        val source = MutationSource()
        val store = loaded(source)
        store.updateStatus("complete")
        source.event(NoteEvent("note:deleted", "note"))
        source.statusWrite.complete(
            TaskUpdateNoteStatusResult(true, "note", "complete", source.fixture("done", "complete"))
        )
        assertTrue(store.state.deleted)
        assertFalse(store.state.saving)
    }

    private fun loaded(source: MutationSource): NoteStore = NoteStore(source).also {
        it.open("ws", "note")
        source.refresh()
        source.complete("todo", "not_started")
    }
}

private class MutationSource : NoteSource {
    val reads = mutableListOf<CompletableFuture<Note>>()
    private val rows = mutableListOf<CompletableFuture<List<NoteTaskRow>>>()
    val lineWrite = CompletableFuture<TaskUpdateResult>()
    val statusWrite = CompletableFuture<TaskUpdateNoteStatusResult>()
    var expected: String? = null
    var expectedVersion: Long? = null
    var statusCalls = 0
    lateinit var refresh: () -> Unit
    lateinit var event: (NoteEvent) -> Unit

    override fun note(workspaceId: String, noteId: String) =
        CompletableFuture<Note>().also(reads::add)
    override fun tasks(workspaceId: String, noteId: String) =
        CompletableFuture<List<NoteTaskRow>>().also(rows::add)
    override fun updateLine(
        workspaceId: String,
        noteId: String,
        line: Int,
        status: String,
        expected: String?
    ): CompletableFuture<TaskUpdateResult> {
        this.expected = expected
        return lineWrite
    }
    override fun updateStatus(
        workspaceId: String,
        noteId: String,
        status: String,
        expectedVersion: Long
    ): CompletableFuture<TaskUpdateNoteStatusResult> {
        this.expectedVersion = expectedVersion
        statusCalls++
        return statusWrite
    }
    override fun watch(
        workspaceId: String,
        onRefresh: () -> Unit,
        onEvent: (NoteEvent) -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        refresh = onRefresh
        event = onEvent
        return Closeable {}
    }
    fun fixture(rowStatus: String, status: String) = Note(
        "note",
        "Task",
        content = if (rowStatus == "done") "- [x] Work" else "- [ ] Work",
        metadata = NoteMetadata(TaskMetadata(status)),
        rev = 7
    )
    fun complete(rowStatus: String, status: String) {
        reads.last().complete(fixture(rowStatus, status))
        rows.last().complete(listOf(NoteTaskRow(1, "Work", rowStatus)))
    }
}
