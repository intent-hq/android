package dev.intent.app.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.markdown.MarkdownNode
import dev.intent.app.markdown.children
import dev.intent.app.markdown.markdownFixture
import dev.intent.app.markdown.parseMarkdown
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow

@Suppress("CyclomaticComplexMethod") // Independent loading, note, task, and error UI states.
@Composable
fun NoteReaderScreen(
    state: NoteReaderState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onTaskStatus: (NoteTaskRow, String) -> Unit = { _, _ -> },
    onNoteStatus: (String) -> Unit = {}
) {
    val body = state.note?.content
    val document = remember(body) { parseMarkdown(body.orEmpty()) }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(state.note?.title ?: "Note", style = MaterialTheme.typography.headlineMedium) }
        if (state.loading || state.saving) item { CircularProgressIndicator() }
        state.mutationError?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        state.note?.metadata?.task?.let { task ->
            item {
                TaskStatusMenu(
                    task.status,
                    taskNoteStatuses,
                    !state.saving && !state.loading,
                    onNoteStatus
                )
            }
        }
        state.error?.let { message ->
            item {
                Column {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        if (state.deleted) item { Text("This note was deleted.") }
        if (state.note != null && body == null) item { Text("The note body is not available.") }
        if (state.note?.contentTruncated ==
            true
        ) {
            item { Text("This note shows only part of its content.") }
        }
        if (body == "") item { Text("This note is empty.") }
        items(document.children()) { MarkdownNode(it) }
        if (state.tasks.isNotEmpty()) {
            item { Text("Tasks", style = MaterialTheme.typography.titleLarge) }
            items(state.tasks) { task ->
                NoteTaskRowView(task, !state.saving && !state.loading) { onTaskStatus(task, it) }
            }
        }
    }
}

@Composable
fun NoteTaskRowView(task: NoteTaskRow, enabled: Boolean = true, onStatus: (String) -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(
            checked = task.status == "done",
            onCheckedChange = { onStatus(if (it) "done" else "todo") },
            enabled = enabled
        )
        Text(task.text, Modifier.weight(1f))
        TaskStatusMenu(task.status, checkboxStatuses, enabled, onStatus)
    }
}

@Suppress("UnusedPrivateMember", "MagicNumber") // Invoked by Android Studio; fixture line numbers.
@Preview(showBackground = true)
@Composable
private fun NoteReaderPreview() {
    MaterialTheme {
        NoteReaderScreen(
            NoteReaderState(
                note = Note(
                    id = "spec",
                    title = "Project plan",
                    content = markdownFixture
                ),
                tasks = listOf(
                    NoteTaskRow(2, "Load the note", "done"),
                    NoteTaskRow(3, "Check the result", "in-progress")
                ),
                loading = false
            ),
            onRetry = {}
        )
    }
}
