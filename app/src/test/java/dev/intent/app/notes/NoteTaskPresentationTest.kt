package dev.intent.app.notes

import dev.intent.app.markdown.children
import dev.intent.app.markdown.markdownRuns
import dev.intent.app.markdown.parseMarkdown
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteMetadata
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.TaskMetadata
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTaskPresentationTest {
    @Test
    fun `tasks bind at source positions while nested and reference content stays intact`() {
        val source = """
            Before

            - [/] **Work** with [reference][ref]
              continuation
              - [ ] Nested

            After

            [ref]: https://example.com
        """.trimIndent()
        val rows = listOf(NoteTaskRow(3, "Work", "in-progress"), NoteTaskRow(5, "Nested", "todo"))
        val document = parseNoteMarkdown(source, rows)
        val items = listItems(document)
        assertEquals(
            listOf(3, 5),
            items.map {
                noteTaskForItem(it, source.lines(), rows)?.lineNumber
            }
        )
        val runs = markdownRuns(document)
        assertTrue(runs.any { it.text == "Work" && it.bold })
        assertTrue(runs.any { it.url == "https://example.com" })
        assertTrue(runs.any { it.text == "continuation" })
        assertFalse(runs.any { "[/]" in it.text })
        assertTrue(markdownRuns(parseMarkdown(source)).any { "[/]" in it.text })
        assertTrue(markdownRuns(parseNoteMarkdown(source)).any { "[/]" in it.text })
    }

    @Test
    fun `code quoted and unsupported bullets never become interactive task rows`() {
        val source = """
            ```
            - [ ] Fenced
            ```

                - [ ] Indented code

            > - [ ] Quoted

            * [ ] Star

            - [ ] Editable
        """.trimIndent()
        val rows = source.lines().mapIndexed { index, text -> NoteTaskRow(index + 1, text, "todo") }
        val items = listItems(parseNoteMarkdown(source, rows))
        val editable = items.mapNotNull { noteTaskForItem(it, source.lines(), rows) }
        assertEquals(listOf(11), editable.map { it.lineNumber })
        assertEquals(3, items.size)
    }

    @Test
    fun `duplicate and out of range server rows do not acquire controls`() {
        val source = "- [ ] Work"
        val item = listItems(parseNoteMarkdown(source)).single()
        val row = NoteTaskRow(1, "Work", "todo")
        assertNull(noteTaskForItem(item, source.lines(), listOf(row, row)))
        assertNull(noteTaskForItem(item, source.lines(), listOf(row.copy(lineNumber = 2))))
        assertNull(noteTaskForItem(item, emptyList(), listOf(row)))
    }

    @Test
    fun `linked controls require the same valid target in source and host row`() {
        val row = NoteTaskRow(1, "Task", "todo", taskNoteId = "task")
        listOf(
            "- [ ] [Task](intent://local/task/other)",
            "- [ ] [Task](intent://local/task/a%2Fb)",
            "- [ ] Task"
        ).forEach { source ->
            val item = listItems(parseNoteMarkdown(source)).single()
            assertNull(noteTaskForItem(item, source.lines(), listOf(row)))
        }
        val source = "- [ ] [Task](intent://local/task/task)"
        val item = listItems(parseNoteMarkdown(source)).single()
        assertEquals(row, noteTaskForItem(item, source.lines(), listOf(row)))
    }

    @Test
    fun `linked card removes obsolete title but preserves nested content for every marker`() {
        listOf(" ", "x", "/").forEach { marker ->
            val source = "- [$marker] [Old title](intent://local/task/task)\n" +
                "  - Nested detail\n\n  Extra paragraph"
            val row = NoteTaskRow(1, "Old title", "todo", taskNoteId = "task")
            val item = listItems(parseNoteMarkdown(source, listOf(row))).first()
            val rendered = linkedNoteTaskBody(item, "task").flatMap { markdownRuns(it) }
                .joinToString("") { it.text }
            assertFalse(rendered.contains("Old title"))
            assertTrue(rendered.contains("Nested detail"))
            assertTrue(rendered.contains("Extra paragraph"))
            assertTrue(markdownRuns(item).any { it.text == "Old title" })
        }
    }

    @Test
    fun `linked card preserves additional inline prose and links to other targets`() {
        listOf(
            "- [ ] [Old title](intent://local/task/task) with extra context",
            "- [ ] [Other task](intent://local/task/other)"
        ).forEach { source ->
            val item = listItems(parseNoteMarkdown(source)).single()
            assertEquals(noteTaskBody(item), linkedNoteTaskBody(item, "task"))
        }
    }

    @Test
    fun `linked ids prefer canonical field and reject malformed navigation targets`() {
        val row = NoteTaskRow(1, "Task", "todo", "canonical", "legacy")
        assertEquals("canonical", linkedTaskId(row))
        assertEquals("legacy", linkedTaskId(row.copy(taskNoteId = null)))
        listOf("", " ", "..", "a/b", "a\\b", "a?b", "a#b", "a\nb").forEach {
            assertNull(linkedTaskId(row.copy(taskNoteId = it)))
        }
    }

    @Test
    fun `reverse dependencies are one hop deduplicated and omit self and archived notes`() {
        fun note(id: String, archived: Boolean = false) = Note(
            id,
            id,
            isArchived = archived,
            metadata = NoteMetadata(TaskMetadata("waiting", dependsOn = listOf("current")))
        )
        val notes = listOf(note("current"), note("b"), note("a"), note("b"), note("hidden", true))
        assertEquals(listOf("a", "b"), dependedOnBy("current", notes))
    }

    @Test
    fun `truncation uses utf8 bytes and does not mistake character counts for bytes`() {
        val note = Note("id", "Title", content = "é", contentLength = 100, contentBytes = 20)
        assertEquals(
            "This note is partly loaded. Showing 2 bytes of 20 bytes.",
            noteTruncationMessage(note)
        )
        assertEquals(
            "This note is partly loaded. Showing 2 bytes.",
            noteTruncationMessage(note.copy(contentBytes = null))
        )
    }

    private fun listItems(node: Node): List<ListItem> =
        listOfNotNull(node as? ListItem) + node.children().flatMap(::listItems)
}
