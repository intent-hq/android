package dev.intent.app.notes

import dev.intent.app.lists.retainMatchingBranches
import dev.intent.core.protocol.models.Note

internal fun filterContextNotes(
    notes: List<Note>,
    query: String,
    currentOnly: Boolean
): List<Note> {
    val search = query.trim()
    return retainMatchingBranches(notes.filterNot { it.isArchived }, { it.id }, { it.parentId }) {
        val current = it.metadata?.task?.status !in setOf("complete", "cancelled")
        (!currentOnly || current) &&
            (
                it.title.contains(search, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(search, ignoreCase = true) }
                )
    }
}
