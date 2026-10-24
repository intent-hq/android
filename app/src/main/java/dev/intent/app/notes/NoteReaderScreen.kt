package dev.intent.app.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow

@Composable
fun NoteReaderScreen(state: NoteReaderState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val body = state.note?.content
    val blocks = remember(body) { markdownBlocks(body.orEmpty()) }
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(state.note?.title ?: "Note", style = MaterialTheme.typography.headlineMedium) }
        if (state.loading) item { CircularProgressIndicator() }
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
        items(blocks) { MarkdownParagraph(it) }
        if (state.tasks.isNotEmpty()) {
            item { Text("Tasks", style = MaterialTheme.typography.titleLarge) }
            items(state.tasks) { NoteTaskRowView(it) }
        }
    }
}

@Composable
private fun MarkdownParagraph(block: MarkdownBlock) {
    val style = when (block.kind) {
        "heading" -> if (block.level ==
            1
        ) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.titleLarge
        }
        else -> MaterialTheme.typography.bodyLarge
    }
    if (block.kind == "code") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(block.text, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace)
        }
    } else {
        Text(markdownInline(block.text), style = style)
    }
}

private val inlinePattern =
    Regex("\\[([^]\\n]+)]\\((https?://[^\\s)]+)\\)|`([^`]+)`|\\*\\*([^*]+)\\*\\*")

@Suppress("MagicNumber") // Regex capture indices.
private fun markdownInline(text: String) = buildAnnotatedString {
    var start = 0
    inlinePattern.findAll(text).forEach { match ->
        append(text.substring(start, match.range.first))
        when {
            match.groupValues[2].isNotEmpty() -> withLink(
                LinkAnnotation.Url(match.groupValues[2])
            ) {
                append(match.groupValues[1])
            }
            match.groupValues[3].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace)
            ) {
                append(match.groupValues[3])
            }
            else -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[4])
            }
        }
        start = match.range.last + 1
    }
    append(text.substring(start))
}

@Composable
fun NoteTaskRowView(task: NoteTaskRow) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (task.status == "done") "☑" else "☐")
        Text(task.text, Modifier.weight(1f))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                task.status,
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
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
                    content =
                    "# Read path\nRead **notes** and `tasks`.\n" +
                        "- Load the note\n1. Check the result\n" +
                        "[Project](https://intentapp.dev)\n```kotlin\nval ready = true\n```"
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
