package dev.intent.app.notes

import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteMetadata
import dev.intent.core.protocol.models.TaskMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextNoteFilterTest {
    private val notes = listOf(
        Note("spec", "Plan"),
        task("done", "Old task", "complete", "spec"),
        task("active", "Fix search", "in_progress", "done"),
        task("cancel", "Cancelled task", "cancelled", "spec"),
        task("waiting", "Wait for review", "waiting", "spec"),
        Note("reference", "Design", tags = listOf("search")),
        Note("archive", "Search archive", isArchived = true)
    )

    @Test
    fun `current keeps open work and its completed ancestors without unrelated closed tasks`() {
        assertEquals(
            listOf("spec", "done", "active", "waiting", "reference"),
            filterContextNotes(notes, "", true).map { it.id }
        )
    }

    @Test
    fun `search ignores case and whitespace and keeps ancestor depths`() {
        val rows = contextNoteRows(filterContextNotes(notes, " SEARCH ", false))
        assertEquals(listOf("spec", "done", "active", "reference"), rows.map { it.note.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })
    }

    @Test
    fun `all restores closed tasks and unmatched search is empty`() {
        assertEquals(notes.dropLast(1), filterContextNotes(notes, "", false))
        assertEquals(emptyList<Note>(), filterContextNotes(notes, "missing", false))
        assertEquals(emptyList<Note>(), filterContextNotes(notes, "Cancelled", true))
    }

    @Test
    fun `search handles cycles and missing parents`() {
        val malformed = listOf(
            Note("a", "A", parentId = "b"),
            Note("b", "Match", parentId = "a"),
            Note("orphan", "Match", parentId = "missing")
        )
        assertEquals(3, contextNoteRows(filterContextNotes(malformed, "match", true)).size)
    }

    private fun task(id: String, title: String, status: String, parent: String) = Note(
        id,
        title,
        parentId = parent,
        metadata = NoteMetadata(TaskMetadata(status))
    )
}
