package dev.intent.app.notes

import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.NoteTasksResult
import dev.intent.core.protocol.models.WireJson
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStoreTest {
    @Test
    fun `subscribe then load note and parse authoritative tasks`() {
        val source = FakeNoteSource()
        val store = NoteStore(source)
        store.open("ws", "spec")
        assertTrue(source.notes.isEmpty())
        source.refresh()
        val tasks = WireJson.decodeFromString(
            NoteTasksResult.serializer(),
            """{"tasks":[{"lineNumber":3,"text":"Read it","status":"in-progress","taskNoteId":"task-a"}]}"""
        ).tasks
        source.complete(0, "Body", tasks)
        assertEquals("Body", store.state.note?.content)
        assertEquals("task-a", store.state.tasks.single().taskNoteId)
        assertFalse(store.state.loading)
    }

    @Test
    fun `live event supersedes earlier in flight read`() {
        val source = FakeNoteSource()
        val store = NoteStore(source)
        store.open("ws", "spec")
        source.refresh()
        source.event(NoteEvent("note:updated", "spec"))
        source.complete(1, "New")
        source.complete(0, "Old")
        assertEquals("New", store.state.note?.content)
    }

    @Test
    fun `reconnect resnapshots and task events refresh linked rows`() {
        val source = FakeNoteSource()
        val store = NoteStore(source)
        store.open("ws", "spec")
        source.refresh()
        source.complete(0, "Before")
        source.refresh()
        source.complete(1, "After reconnect")
        source.event(NoteEvent("task:status:changed", "linked-task"))
        source.complete(2, "After task", listOf(NoteTaskRow(1, "Linked", "done")))
        assertEquals("done", store.state.tasks.single().status)
        assertEquals("After task", store.state.note?.content)
    }

    @Test
    fun `delete and close fence late replies and callbacks`() {
        val source = FakeNoteSource()
        val store = NoteStore(source)
        store.open("ws", "spec")
        source.refresh()
        source.event(NoteEvent("note:deleted", "spec"))
        source.complete(0, "Stale")
        assertTrue(store.state.deleted)
        assertNull(store.state.note)
        store.close()
        source.refresh()
        assertEquals(1, source.notes.size)
        assertTrue(source.closed)
    }

    @Test
    fun `failed refresh retains previous body and retry recovers`() {
        val source = FakeNoteSource()
        val store = NoteStore(source)
        store.open("ws", "spec")
        source.refresh()
        source.complete(0, "Saved")
        source.refresh()
        source.notes[1].completeExceptionally(IllegalStateException("offline"))
        source.rows[1].complete(emptyList())
        assertEquals("Saved", store.state.note?.content)
        assertTrue(store.state.error != null)
        store.refresh()
        source.complete(2, "")
        assertEquals("", store.state.note?.content)
        assertNull(store.state.error)
    }

    @Test
    fun `switching note rejects old read and event callback`() {
        val source = FakeNoteSource()
        val store = NoteStore(source)
        store.open("ws", "first")
        source.refresh()
        val oldEvent = source.event
        store.open("ws", "second")
        source.refresh()
        source.complete(1, "Second")
        source.complete(0, "First")
        oldEvent(NoteEvent("note:deleted", "second"))
        assertEquals("Second", store.state.note?.content)
        assertFalse(store.state.deleted)
    }

    @Test
    fun `Markdown parses headings lists and code without parsing code content`() {
        val blocks =
            markdownBlocks(
                "# Title\n- Item\n1. Ordered\n```\n# code\n```\n[Link](https://example.com)"
            )
        assertEquals(listOf("heading", "list", "list", "code", "text"), blocks.map { it.kind })
        assertEquals("# code", blocks[3].text)
        assertEquals("code", markdownBlocks("~~~\nunfinished").single().kind)
    }
}

private class FakeNoteSource : NoteSource {
    val notes = mutableListOf<CompletableFuture<Note>>()
    val rows = mutableListOf<CompletableFuture<List<NoteTaskRow>>>()
    lateinit var refresh: () -> Unit
    lateinit var event: (NoteEvent) -> Unit
    var closed = false

    override fun note(workspaceId: String, noteId: String) = CompletableFuture<Note>().also {
        notes.add(it)
    }
    override fun tasks(workspaceId: String, noteId: String) =
        CompletableFuture<List<NoteTaskRow>>().also { rows.add(it) }
    override fun watch(
        workspaceId: String,
        onRefresh: () -> Unit,
        onEvent: (NoteEvent) -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        refresh = onRefresh
        event = onEvent
        return Closeable { closed = true }
    }

    fun complete(index: Int, content: String, tasks: List<NoteTaskRow> = emptyList()) {
        notes[index].complete(Note("spec", "Spec", content = content))
        rows[index].complete(tasks)
    }
}
