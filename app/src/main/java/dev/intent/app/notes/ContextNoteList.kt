package dev.intent.app.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.lists.ListFilters
import dev.intent.core.protocol.models.Note

private const val MAX_VISIBLE_DEPTH = 4

@Composable
internal fun ContextNoteList(
    notes: List<Note>,
    onSelected: (String) -> Unit,
    loading: Boolean = false,
    error: String? = null,
    files: (LazyListScope.() -> Unit)? = null
) {
    var filesExpanded by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var currentOnly by rememberSaveable { mutableStateOf(false) }
    var collapsed by remember(query, currentOnly) { mutableStateOf(emptySet<String>()) }
    val rows = remember(notes, collapsed, query, currentOnly) {
        contextNoteRows(filterContextNotes(notes, query, currentOnly), collapsed)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = IntentScreen.pageInsets) {
        error?.let { item { Text(it, color = IntentScreen.error) } }
        if (!loading && error == null && notes.isEmpty()) item { Text("No notes") }
        item {
            ListFilters(query, currentOnly, "Search notes and tasks", { query = it }, {
                currentOnly =
                    it
            })
        }
        if (rows.isEmpty() && notes.isNotEmpty()) {
            item { Text("No matching notes or tasks. Change the search or select All.") }
        }
        items(rows, key = { it.note.id }) { row ->
            ContextRow(row, row.note.id in collapsed, onSelected) {
                collapsed =
                    if (row.note.id in
                        collapsed
                    ) {
                        collapsed - row.note.id
                    } else {
                        collapsed + row.note.id
                    }
            }
        }
        if (files != null) {
            item(key = "files.heading") {
                TextButton(onClick = {
                    filesExpanded = !filesExpanded
                }, modifier = Modifier.fillMaxWidth().padding(top = IntentScreen.sectionSpacing)) {
                    Text(
                        if (filesExpanded) "Files ⌄" else "Files ›",
                        Modifier.fillMaxWidth(),
                        color = IntentScreen.text,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
            if (filesExpanded) files()
        }
    }
}

@Composable
private fun ContextRow(
    row: ContextNoteRow,
    collapsed: Boolean,
    onSelected: (String) -> Unit,
    onToggle: () -> Unit
) {
    val note = row.note
    Row(
        Modifier.fillMaxWidth().padding(
            start =
            IntentSpacing.row * row.depth.coerceAtMost(MAX_VISIBLE_DEPTH)
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f).clickable {
                onSelected(note.id)
            }.padding(vertical = IntentSpacing.row),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.row)) {
                NoteTypeIcon(note)
                Text(
                    note.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row(
                Modifier.padding(start = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val type = when {
                    note.isDefault || note.id == "spec" -> "Spec"
                    note.metadata?.task != null -> "Task"
                    else -> "Note"
                }
                Text(
                    type,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                note.metadata?.task?.status?.let { ContextStatus(it) }
            }
        }
        if (row.hasChildren) {
            TextButton(
                onClick = onToggle,
                modifier = Modifier.semantics {
                    contentDescription =
                        "${if (collapsed) "Expand" else "Collapse"} child notes for ${note.title}"
                }
            ) {
                Text(
                    if (collapsed) "+" else "−",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ContextStatus(status: String) {
    val colors = MaterialTheme.colorScheme
    val attention = status == "blocked" || status == "discussion_needed"
    Surface(
        color = if (attention) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (attention) colors.onErrorContainer else colors.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            contextStatusLabel(status),
            Modifier.padding(horizontal = IntentSpacing.medium, vertical = IntentSpacing.small),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Suppress("MagicNumber") // Normalized coordinates for the note and checkbox glyphs.
@Composable
private fun NoteTypeIcon(note: Note) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val task = note.metadata?.task
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.5.dp.toPx()
        drawRect(
            color,
            Offset(stroke, stroke),
            Size(
                size.width - stroke * 2,
                size.height - stroke * 2
            ),
            style = Stroke(stroke)
        )
        if (task != null) {
            if (task.status == "complete") {
                drawLine(
                    color,
                    Offset(size.width * .2f, size.height * .5f),
                    Offset(
                        size.width * .45f,
                        size.height * .7f
                    ),
                    stroke
                )
                drawLine(
                    color,
                    Offset(size.width * .45f, size.height * .7f),
                    Offset(
                        size.width * .8f,
                        size.height * .3f
                    ),
                    stroke
                )
            }
        } else {
            for (line in 1..3) {
                val y = size.height * line / 4
                drawLine(color, Offset(size.width * .25f, y), Offset(size.width * .75f, y), stroke)
            }
        }
    }
}
