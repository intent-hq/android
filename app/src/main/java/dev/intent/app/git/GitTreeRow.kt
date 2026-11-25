package dev.intent.app.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.core.protocol.models.GitFileStatus

internal data class GitTreeRow(
    val path: String,
    val depth: Int,
    val file: GitFileStatus? = null,
    val count: Int = 1
) {
    val name: String get() = path.substringAfterLast('/')
    val key: String get() = "${if (file == null) "folder" else "file"}:$path"
}

internal fun gitTreeRows(files: List<GitFileStatus>, collapsed: Set<String>): List<GitTreeRow> {
    val directories = linkedMapOf<String, Int>()
    files.forEach { file ->
        val parts = file.path.split('/')
        for (depth in 1 until parts.size) {
            val path = parts.take(depth).joinToString("/")
            directories[path] = (directories[path] ?: 0) + 1
        }
    }
    val rows =
        directories.map { (path, count) ->
            GitTreeRow(path, path.count { it == '/' }, count = count)
        } +
            files.map { GitTreeRow(it.path, it.path.count { char -> char == '/' }, it) }
    val children = rows.groupBy { it.path.substringBeforeLast('/', "") }
    val result = mutableListOf<GitTreeRow>()
    val pending = ArrayDeque<GitTreeRow>()
    fun addChildren(path: String) {
        children[path].orEmpty().sortedWith(
            compareBy<GitTreeRow> {
                it.file != null
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.key }
        ).asReversed().forEach(pending::addLast)
    }
    addChildren("")
    while (pending.isNotEmpty()) {
        val row = pending.removeLast()
        result.add(row)
        if (row.file == null && row.path !in collapsed) addChildren(row.path)
    }
    return result
}

@Composable
internal fun GitTreeItem(
    row: GitTreeRow,
    staged: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit
) {
    val stage = if (staged) "staged" else "unstaged"
    val folder = row.file == null
    val indent = (minOf(row.depth, MAX_DEPTH) * INDENT).dp
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .testTag("changes.${if (folder) "folder" else "file"}.$stage.${row.path}")
        .clickable(role = Role.Button, onClick = onClick)
        .let { base ->
            if (folder) {
                base.semantics {
                    contentDescription = "Folder ${row.path}, ${row.count} files"
                    stateDescription = if (collapsed) "Collapsed" else "Expanded"
                }
            } else {
                base
            }
        }
        .padding(start = indent, top = 10.dp, bottom = 10.dp)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val name = if (row.depth >= MAX_DEPTH) row.path else row.name
                Text(
                    if (folder) {
                        "${if (collapsed) "▸" else "▾"}  $name (${row.count})"
                    } else {
                        "${row.file?.status}  $name"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = IntentScreen.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                row.file?.metadata?.let { metadata ->
                    val stats = if (metadata.stats.binary) {
                        "Binary"
                    } else {
                        "+${metadata.stats.additions} −${metadata.stats.deletions}"
                    }
                    Text(
                        listOfNotNull(metadata.agentName, stats).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentScreen.secondaryText
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private const val MAX_DEPTH = 6
private const val INDENT = 16
