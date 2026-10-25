package dev.intent.app.notes

import dev.intent.core.protocol.models.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextNoteTreeTest {
    @Test
    fun `spec comes first and children remain beneath their parent`() {
        val rows = contextNoteRows(
            listOf(
                Note("other", "Other"),
                Note("task", "Task", parentId = "spec"),
                Note("spec", "Spec")
            )
        )
        assertEquals(listOf("spec", "task", "other"), rows.map { it.note.id })
        assertEquals(listOf(0, 1, 0), rows.map { it.depth })
        assertEquals(listOf(true, false, false), rows.map { it.hasChildren })
    }

    @Test
    fun `collapse hides descendants but keeps unrelated notes`() {
        val notes = listOf(
            Note("spec", "Spec"),
            Note("task", "Task", parentId = "spec"),
            Note("nested", "Nested", parentId = "task"),
            Note("other", "Other")
        )
        assertEquals(
            listOf("spec", "other"),
            contextNoteRows(notes, setOf("spec")).map {
                it.note.id
            }
        )
        assertEquals(4, contextNoteRows(notes).size)
    }

    @Test
    fun `orphans and cycles stay reachable and archived notes stay hidden`() {
        val notes = listOf(
            Note("archived", "Archived", isArchived = true),
            Note("orphan", "Orphan", parentId = "archived"),
            Note("a", "A", parentId = "b"),
            Note("b", "B", parentId = "a"),
            Note("a", "Duplicate", parentId = "b")
        )
        assertEquals(listOf("orphan", "a", "b"), contextNoteRows(notes).map { it.note.id })
    }

    @Test
    fun `task labels are readable including future states`() {
        assertEquals("Needs review", contextStatusLabel("review_required"))
        assertEquals("In progress", contextStatusLabel("in_progress"))
        assertEquals("Future state", contextStatusLabel("future_state"))
    }
}
