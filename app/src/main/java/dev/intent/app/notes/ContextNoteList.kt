package dev.intent.app.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.core.protocol.models.Note

private const val MAX_VISIBLE_DEPTH = 4

// iOS WorkspaceDetailStyle note spacing: 48 between root notes, 24 between children.
private val ROOT_NOTE_SPACING = 48.dp
private val CHILD_NOTE_SPACING = 24.dp
private val NOTE_INDENT = 48.dp
private val ICON_COLUMN = 48.dp

@Composable
internal fun ContextNoteList(
    notes: List<Note>,
    onSelected: (String) -> Unit,
    loading: Boolean = false,
    error: String? = null
) {
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val rows = remember(notes, collapsed) { contextNoteRows(notes, collapsed) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = IntentSpacing.page,
            vertical = IntentSpacing.medium
        )
    ) {
        contextNoteItems(
            rows = rows,
            collapsed = collapsed,
            loading = loading,
            error = error,
            notes = notes,
            onSelected = onSelected,
            onToggle = { id ->
                collapsed = if (id in collapsed) collapsed - id else collapsed + id
            }
        )
    }
}

@Suppress("LongParameterList")
internal fun LazyListScope.contextNoteItems(
    rows: List<ContextNoteRow>,
    collapsed: Set<String>,
    loading: Boolean,
    error: String?,
    notes: List<Note>,
    onSelected: (String) -> Unit,
    onToggle: (String) -> Unit
) {
    error?.let { item(key = "notes.error") { Text(it, color = IntentScreen.error) } }
    if (!loading && error == null && notes.isEmpty()) {
        item(key = "notes.empty") { Text("No notes", color = IntentScreen.secondaryText) }
    }
    items(rows, key = { "note.${it.note.id}" }) { row ->
        val icon = remember(row.note, notes) { noteIconKind(row.note, notes) }
        ContextRow(row, icon, row === rows.first(), row.note.id in collapsed, onSelected) {
            onToggle(row.note.id)
        }
    }
}

/** iOS NoteRowView: the leading icon is the disclosure, and titles start where child icons do. */
@Suppress("LongParameterList")
@Composable
private fun ContextRow(
    row: ContextNoteRow,
    icon: NoteIconKind,
    first: Boolean,
    collapsed: Boolean,
    onSelected: (String) -> Unit,
    onToggle: () -> Unit
) {
    val note = row.note
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = NOTE_INDENT * row.depth.coerceAtMost(MAX_VISIBLE_DEPTH),
                top = noteRowTopSpacing(first, row.depth)
            )
            .clickable { onSelected(note.id) },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(ICON_COLUMN)
                .then(
                    if (row.hasChildren) {
                        Modifier
                            .clickable(onClick = onToggle)
                            .semantics {
                                contentDescription =
                                    "${if (collapsed) "Expand" else "Collapse"} child notes for ${note.title}"
                                stateDescription = if (collapsed) "Collapsed" else "Expanded"
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            NoteRowIcon(icon)
        }
        Text(
            note.title,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 24.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            color = MaterialTheme.colorScheme.onSurface,
            style = if (row.depth == 0) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            }
        )
    }
}

private fun noteRowTopSpacing(first: Boolean, depth: Int) = when {
    first -> 0.dp
    depth == 0 -> ROOT_NOTE_SPACING
    else -> CHILD_NOTE_SPACING
}
