package dev.intent.app.notes

import dev.intent.app.markdown.children
import dev.intent.app.markdown.markdownNoteId
import dev.intent.app.markdown.markdownRuns
import dev.intent.app.markdown.parseMarkdown
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text

private val checkboxLine = Regex("""^[ \t]*- \[[ xX/]\]\s*(.*)$""")

/** Preserve the full Markdown tree and reference-link context, including nested lists. */
internal fun parseNoteMarkdown(content: String, tasks: List<NoteTaskRow> = emptyList()): Node {
    val document = parseMarkdown(content, sourcePositions = true)
    val lines = content.lines()
    fun visit(node: Node) {
        if (node is ListItem) {
            val line = node.sourceSpans.firstOrNull()?.lineIndex?.let(lines::getOrNull)
            if (line != null &&
                noteTaskForItem(node, lines, tasks) != null &&
                line.trimStart().startsWith("- [/]")
            ) {
                val text = node.children().filterIsInstance<Paragraph>().firstOrNull()?.firstChild
                if (text is Text && text.literal.startsWith("[/]")) {
                    text.literal = text.literal.removePrefix("[/]").trimStart()
                }
            }
        }
        node.children().forEach(::visit)
    }
    visit(document)
    return document
}

/** Never bind a server row to a different source line or a checkbox inside a code fence. */
@Suppress("ReturnCount") // Reject ambiguous source/host mappings before enabling writes.
internal fun noteTaskForItem(
    item: ListItem,
    lines: List<String>,
    tasks: List<NoteTaskRow>
): NoteTaskRow? {
    val line = item.sourceSpans.firstOrNull()?.lineIndex ?: return null
    val source = lines.getOrNull(line) ?: return null
    if (!checkboxLine.matches(source)) return null
    val task = tasks.singleOrNull { it.lineNumber == line + 1 } ?: return null
    val linked = linkedTaskId(task)
    if ((task.taskNoteId != null || task.linkedTaskNoteId != null) && linked == null) return null
    if (linked != null) {
        val paragraph = item.children().filterIsInstance<Paragraph>().firstOrNull() ?: return null
        if (markdownRuns(paragraph).none { it.url?.let(::markdownNoteId) == linked }) return null
    }
    return task
}

internal fun noteTaskBody(item: ListItem): List<Node> =
    item.children().filterNot { it is TaskListItemMarker }

/** The card title replaces only a title-only first paragraph, never prose or nested blocks. */
internal fun linkedNoteTaskBody(item: ListItem, linkedId: String): List<Node> =
    noteTaskBody(item).filterIndexed { index, node ->
        val content = (node as? Paragraph)?.children()?.filterNot {
            it is TaskListItemMarker || (it is Text && it.literal.isBlank())
        }
        val link = content?.singleOrNull() as? Link
        index != 0 || link == null || markdownNoteId(link.destination) != linkedId
    }

internal fun linkedTaskId(task: NoteTaskRow): String? =
    (task.taskNoteId ?: task.linkedTaskNoteId)?.takeIf(::validNoteReference)

internal fun validNoteReference(id: String): Boolean = id.isNotBlank() &&
    id != "." &&
    id != ".." &&
    id.none { it == '/' || it == '\\' || it == '?' || it == '#' || it.isISOControl() }

/** Relations are one-hop links, never recursively rendered, so cycles are harmless. */
internal fun dependedOnBy(noteId: String, notes: List<Note>): List<String> = notes
    .filter {
        !it.isArchived && it.id != noteId && noteId in it.metadata?.task?.dependsOn.orEmpty()
    }
    .map { it.id }.distinct().sorted()

internal fun noteTruncationMessage(note: Note): String {
    val shown = note.content.orEmpty().toByteArray(Charsets.UTF_8).size
    val total = note.contentBytes?.takeIf { it > shown }
    return "This note is partly loaded. Showing $shown bytes" +
        (total?.let { " of $it bytes." } ?: ".")
}
