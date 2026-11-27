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
    "in_progress", "in-progress" -> "In progress"
    "todo" -> "To do"
    "done" -> "Done"
    "review_required" -> "Needs review"
    "discussion_needed" -> "Needs discussion"
    "complete" -> "Complete"
    "cancelled" -> "Cancelled"
    "waiting" -> "Waiting"
    "blocked" -> "Blocked"
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal data class TaskStats(val total: Int, val completed: Int, val inProgress: Int)

/** iOS NoteRowIcon: tasks show their status, specs a file list, and notes with child tasks a progress ring. */
internal sealed interface NoteIconKind {
    data object Document : NoteIconKind
    data object Spec : NoteIconKind
    data class Progress(val stats: TaskStats) : NoteIconKind
    data class Task(val status: String) : NoteIconKind
}

internal fun noteIconKind(note: Note, notes: List<Note>): NoteIconKind {
    val children = notes.filter { !it.isArchived && it.parentId == note.id }
    val status = note.metadata?.task?.status
    val tasks = children.mapNotNull { it.metadata?.task?.status }
    return when {
        status != null -> NoteIconKind.Task(derivedTaskStatus(children, status))
        note.isDefault || note.id == "spec" -> NoteIconKind.Spec
        tasks.isEmpty() -> NoteIconKind.Document
        else -> NoteIconKind.Progress(
            TaskStats(
                total = tasks.size,
                completed = tasks.count { it == "complete" },
                inProgress = tasks.count { it == "in_progress" || it == "in-progress" }
            )
        )
    }
}

private fun derivedTaskStatus(children: List<Note>, status: String): String {
    val statuses = children.mapNotNull { it.metadata?.task?.status }
    return when {
        statuses.isEmpty() -> status
        statuses.all { it == "complete" } -> "complete"
        statuses.all { it == "not_started" } -> "not_started"
        else -> "in_progress"
    }
}
