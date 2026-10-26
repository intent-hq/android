package dev.intent.app.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import dev.intent.app.design.IntentScreen

internal fun LazyListScope.workspaceFiles(
    state: WorkspaceFilesState,
    onToggle: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSelected: (String) -> Unit
) {
    item(key = "files.root") { DirectoryStatus(state, ".", onRetry) }
    items(state.rows, key = { "files.${it.entry.path}" }) { row ->
        val entry = row.entry
        val expanded = entry.path in state.expanded
        Column(Modifier.padding(start = 16.dp * row.depth.coerceAtMost(MAX_TREE_DEPTH))) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("files.${if (entry.isDirectory) "folder" else "file"}.${entry.path}")
                    .clickable(role = Role.Button) {
                        if (entry.isDirectory) onToggle(entry.path) else onSelected(entry.path)
                    }.semantics {
                        contentDescription =
                            if (entry.isDirectory) "Folder ${entry.path}" else entry.path
                        if (entry.isDirectory) {
                            stateDescription =
                                if (expanded) "Expanded" else "Collapsed"
                        }
                    }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
            ) {
                Icon(
                    painterResource(
                        if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
                    ),
                    null,
                    Modifier.size(20.dp),
                    tint = IntentScreen.secondaryText
                )
                Text(
                    if (row.depth >= MAX_TREE_DEPTH) entry.path else entry.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (entry.isDirectory) {
                    Text(
                        if (expanded) "⌄" else "›",
                        color = IntentScreen.secondaryText
                    )
                }
            }
            if (entry.isDirectory && expanded) DirectoryStatus(state, entry.path, onRetry)
        }
    }
}

@androidx.compose.runtime.Composable
private fun DirectoryStatus(state: WorkspaceFilesState, path: String, retry: (String) -> Unit) {
    when {
        path in state.errors -> Column {
            Text(requireNotNull(state.errors[path]), color = IntentScreen.error)
            TextButton(onClick = {
                retry(path)
            }, modifier = Modifier.testTag("files.retry.$path")) { Text("Retry") }
        }
        path in state.loading || (path == "." && path !in state.entries) -> Text("Loading files…")
        state.entries[path]?.isEmpty() == true -> Text(
            if (path == ".") "No files yet" else "Empty folder",
            color = IntentScreen.secondaryText
        )
    }
}

private const val MAX_TREE_DEPTH = 3
