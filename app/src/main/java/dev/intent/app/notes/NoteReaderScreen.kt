package dev.intent.app.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.markdown.LocalMarkdownListItemRenderer
import dev.intent.app.markdown.LocalMarkdownNoteLink
import dev.intent.app.markdown.MarkdownNode
import dev.intent.app.markdown.children
import dev.intent.app.markdown.markdownNoteId
import dev.intent.app.markdown.markdownRuns
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Paragraph

// Independent note, task, and error states with explicit page actions.
@Suppress("CyclomaticComplexMethod", "LongParameterList", "LongMethod")
@Composable
fun NoteReaderScreen(
    state: NoteReaderState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onTaskStatus: (NoteTaskRow, String) -> Unit = { _, _ -> },
    onNoteStatus: (String) -> Unit = {},
    actions: List<ChromeAction> = emptyList(),
    onNoteSelected: ((String) -> Unit)? = null,
    onAgentSelected: ((String) -> Unit)? = null,
    onLinkedTaskStatus: (String, String) -> Unit = { _, _ -> }
) {
    val body = state.note?.content
    val document = remember(body, state.tasks) { parseNoteMarkdown(body.orEmpty(), state.tasks) }
    val lines = remember(body) { body.orEmpty().lines() }
    val onNote = onNoteSelected ?: LocalMarkdownNoteLink.current
    val enabled = !state.saving && !state.loading && state.error == null
    val notes = state.relatedNotes.filterNot { it.id == state.note?.id } + listOfNotNull(state.note)
    Column(modifier.fillMaxSize()) {
        CompactAppBar(
            state.note?.title ?: "Note",
            actions = listOf(ChromeAction("Refresh", run = onRetry)) + actions
        )
        LazyColumn(
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            if (state.loading || state.saving) item { CircularProgressIndicator() }
            state.mutationError?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            state.note?.let { note ->
                if (note.tags.isNotEmpty()) {
                    item { Text(note.tags.joinToString(" · ") { "#$it" }) }
                }
                if (note.metadata?.task != null) {
                    item {
                        NoteTaskMetadata(
                            note,
                            notes,
                            enabled,
                            onNoteStatus,
                            onNote,
                            onAgentSelected
                        )
                    }
                }
            }
            state.relatedNotesError?.let { message -> item { Text(message) } }
            state.error?.let { message ->
                item {
                    Column {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            if (state.deleted) item { Text("This note was deleted.") }
            if (state.note != null && body == null) {
                item { Text("The note body is not available.") }
            }
            state.note?.takeIf { it.contentTruncated }?.let { note ->
                item {
                    Column {
                        Text(noteTruncationMessage(note))
                        TextButton(onClick = onRetry) { Text("Retry loading note") }
                    }
                }
            }
            if (body == "") item { Text("This note is empty.") }
            items(document.children()) { node ->
                CompositionLocalProvider(
                    LocalMarkdownNoteLink provides onNote,
                    LocalMarkdownListItemRenderer provides { item ->
                        val task = noteTaskForItem(item, lines, state.tasks)
                        if (task == null) {
                            false
                        } else {
                            InlineNoteTask(
                                item,
                                task,
                                notes,
                                enabled,
                                onNote,
                                { onTaskStatus(task, it) },
                                onLinkedTaskStatus
                            )
                            true
                        }
                    }
                ) {
                    MarkdownNode(node)
                }
            }
        }
    }
}

@Suppress("LongParameterList") // Plain rows and linked cards use different protocol mutations.
@Composable
private fun InlineNoteTask(
    item: ListItem,
    task: NoteTaskRow,
    notes: List<Note>,
    enabled: Boolean,
    onNote: ((String) -> Unit)?,
    onStatus: (String) -> Unit,
    onLinkedStatus: (String, String) -> Unit
) {
    val linkedId = linkedTaskId(task)
    if (linkedId == null) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = task.status == "done",
                    onCheckedChange = { onStatus(if (it) "done" else "todo") },
                    enabled = enabled
                )
                Column(Modifier.weight(1f)) {
                    noteTaskBody(item).forEach { MarkdownNode(it) }
                    TaskStatusMenu(task.status, checkboxStatuses, enabled, onStatus)
                }
            }
        }
    } else {
        val linked = notes.firstOrNull { it.id == linkedId && !it.isArchived }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val title = linked?.title ?: noteTaskBody(item).firstOrNull()?.let {
                    markdownRuns(it).joinToString("") { run -> run.text }
                } ?: task.text
                if (onNote != null) {
                    TextButton(onClick = { onNote(linkedId) }) { Text(title) }
                } else {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
                TaskStatusMenu(
                    linked?.metadata?.task?.status ?: "Details unavailable",
                    taskNoteStatuses,
                    enabled && linked?.metadata?.task != null,
                    { onLinkedStatus(linkedId, it) }
                )
                val body = noteTaskBody(item)
                body.filterIndexed { index, node ->
                    val link = (node as? Paragraph)?.children()?.singleOrNull() as? Link
                    index != 0 || link == null || markdownNoteId(link.destination) != linkedId
                }.forEach { MarkdownNode(it) }
            }
        }
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
                    content = "Project plan\n\n- [x] Load the note\n- [/] Check the result"
                ),
                tasks = listOf(
                    NoteTaskRow(3, "Load the note", "done"),
                    NoteTaskRow(4, "Check the result", "in-progress")
                ),
                loading = false
            ),
            onRetry = {}
        )
    }
}
