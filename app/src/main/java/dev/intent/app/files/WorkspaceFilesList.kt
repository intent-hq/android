package dev.intent.app.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import dev.intent.app.R
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.TREE_GUIDE_ALPHA
import dev.intent.app.design.TREE_MAX_DEPTH
import dev.intent.app.design.TreeFileIconSize
import dev.intent.app.design.TreeIconSize
import dev.intent.app.design.TreeRowHeight
import dev.intent.app.design.TreeRowPadding
import dev.intent.app.design.TreeRowSpacing
import dev.intent.app.design.treeDisplayName
import dev.intent.app.design.treeGuides
import dev.intent.app.design.treeIndentation
import dev.intent.app.design.treeNameStyle

internal fun LazyListScope.workspaceFiles(
    state: WorkspaceFilesState,
    onToggle: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSelected: (String) -> Unit,
    query: String = ""
) {
    item(key = "files.root") { DirectoryStatus(state, ".", onRetry) }
    val rows = fileSearchRows(state, query)
    if (query.isNotBlank() && rows.isEmpty()) {
        item { Text("No matching loaded files. Open folders to load more files.") }
    }
    items(rows, key = { "files.${it.entry.path}" }) { row ->
        val entry = row.entry
        val expanded = entry.path in state.expanded
        val depth = if (query.isBlank()) row.depth else 0
        Column {
            FileTreeRow(row, depth, expanded, query.isNotBlank()) {
                if (entry.isDirectory) onToggle(entry.path) else onSelected(entry.path)
            }
            if (entry.isDirectory && expanded) {
                Box(Modifier.padding(start = treeIndentation(depth + 1))) {
                    DirectoryStatus(state, entry.path, onRetry)
                }
            }
        }
    }
}

/** iOS WorkspaceFileTreeLabel: the Changes tree grid with guides, monospace names, and no chevrons. */
@androidx.compose.runtime.Composable
private fun FileTreeRow(
    row: WorkspaceFileRow,
    depth: Int,
    expanded: Boolean,
    fullPath: Boolean,
    onClick: () -> Unit
) {
    val entry = row.entry
    val folder = entry.isDirectory
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TreeRowHeight)
            .testTag("files.${if (folder) "folder" else "file"}.${entry.path}")
            .treeGuides(
                depth,
                IntentScreen.secondaryText.copy(alpha = TREE_GUIDE_ALPHA),
                ownGuide = folder && expanded && depth < TREE_MAX_DEPTH
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (folder) "Folder ${entry.path}" else entry.path
                if (folder) stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(start = treeIndentation(depth), top = TreeRowPadding, bottom = TreeRowPadding),
        verticalAlignment = if (folder) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(TreeRowSpacing)
    ) {
        Box(Modifier.size(TreeIconSize), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (folder) R.drawable.ic_folder else R.drawable.ic_file),
                null,
                Modifier.size(if (folder) TreeIconSize else TreeFileIconSize),
                tint = IntentScreen.secondaryText
            )
        }
        Text(
            if (fullPath) entry.path else treeDisplayName(entry.path, row.depth),
            Modifier.weight(1f),
            style = treeNameStyle,
            color = IntentScreen.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        path in state.loading || (path == "." && path !in state.entries) -> Text(
            "Loading files…",
            Modifier.padding(vertical = TreeRowPadding),
            color = IntentScreen.secondaryText
        )
        state.entries[path]?.isEmpty() == true -> Text(
            if (path == ".") "No files yet" else "Empty folder",
            Modifier.padding(vertical = TreeRowPadding),
            color = IntentScreen.secondaryText
        )
    }
}

private fun fileSearchRows(state: WorkspaceFilesState, query: String) = if (query.isBlank()) {
    state.rows
} else {
    state.entries.values.flatten()
        .filter { it.path.contains(query.trim(), ignoreCase = true) }
        .distinctBy { it.path }.map { WorkspaceFileRow(it, 0) }
}
