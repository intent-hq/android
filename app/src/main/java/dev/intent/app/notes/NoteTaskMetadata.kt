package dev.intent.app.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.core.protocol.models.Note

@Suppress("LongParameterList") // Status writes and note/agent navigation are independent actions.
@Composable
internal fun NoteTaskMetadata(
    note: Note,
    notes: List<Note>,
    enabled: Boolean,
    onStatus: (String) -> Unit,
    onNote: ((String) -> Unit)?,
    onAgent: ((String) -> Unit)?
) {
    val task = note.metadata?.task ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskStatusMenu(task.status, taskNoteStatuses, enabled, onStatus)
        if (task.assignedAgentIds.isNotEmpty()) {
            Text("Assigned agents", style = MaterialTheme.typography.labelLarge)
            task.assignedAgentIds.distinct().forEach { id ->
                if (onAgent == null) {
                    Text(id, style = MaterialTheme.typography.bodyMedium)
                } else {
                    TextButton(onClick = { onAgent(id) }) { Text(id) }
                }
            }
        }
        NoteRelations(
            "Depends on",
            task.dependsOn,
            notes,
            if (task.status == "complete") emptyList() else task.unmetDependsOn,
            onNote
        )
        NoteRelations("Depended on by", dependedOnBy(note.id, notes), notes, emptyList(), onNote)
        NoteRelations("Conflicts with", task.conflictsWith, notes, emptyList(), onNote)
        task.blockedReason?.takeIf { it.isNotBlank() }?.let {
            Text("Blocked: $it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoteRelations(
    label: String,
    ids: List<String>,
    notes: List<Note>,
    unmet: List<String>,
    onNote: ((String) -> Unit)?
) {
    if (ids.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelLarge)
    ids.distinct().forEach { id ->
        val related = notes.firstOrNull { it.id == id && !it.isArchived }
        val status = related?.metadata?.task?.status?.let(::contextStatusLabel)
        val text = listOfNotNull(
            related?.title ?: id,
            status,
            "Unmet dependency".takeIf { id in unmet }
        ).joinToString(" · ")
        if (onNote != null && validNoteReference(id)) {
            TextButton(onClick = { onNote(id) }, modifier = Modifier.fillMaxWidth()) {
                Text(text, Modifier.fillMaxWidth())
            }
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
