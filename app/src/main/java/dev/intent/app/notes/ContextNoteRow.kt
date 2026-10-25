package dev.intent.app.notes

import dev.intent.core.protocol.models.Note

internal data class ContextNoteRow(val note: Note, val depth: Int, val hasChildren: Boolean)

/** Keep orphaned notes and malformed cycles reachable, without duplicating rows. */
internal fun contextNoteRows(
    notes: List<Note>,
    collapsed: Set<String> = emptySet()
): List<ContextNoteRow> {
    val visible = notes.filterNot { it.isArchived }.distinctBy { it.id }.sortedWith(
        compareByDescending<Note> { it.isDefault || it.id == "spec" }
            .thenBy { it.createdAt }
    )
    val ids = visible.map { it.id }.toSet()
    val children = visible.groupBy { it.parentId }
    val visited = mutableSetOf<String>()
    val rows = mutableListOf<ContextNoteRow>()
    fun visit(note: Note, depth: Int, hidden: Boolean = false) {
        if (!visited.add(note.id)) return
        val nested = children[note.id].orEmpty()
        if (!hidden) rows.add(ContextNoteRow(note, depth, nested.isNotEmpty()))
        nested.forEach { visit(it, depth + 1, hidden || note.id in collapsed) }
    }
    visible.filter { it.parentId !in ids }.forEach { visit(it, 0) }
    visible.filterNot { it.id in visited }.forEach { visit(it, 0) }
    return rows
}

internal fun contextStatusLabel(status: String): String = when (status) {
    "not_started" -> "Not started"
    "in_progress" -> "In progress"
    "review_required" -> "Needs review"
    "discussion_needed" -> "Needs discussion"
    "complete" -> "Complete"
    "cancelled" -> "Cancelled"
    "waiting" -> "Waiting"
    "blocked" -> "Blocked"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
