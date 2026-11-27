package dev.intent.app.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.LocalIntentDarkTheme
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

/** Deep rows stop indenting and show the trailing path segments instead. */
internal fun gitTreeDisplayName(row: GitTreeRow): String = treeDisplayName(row.path, row.depth)

@Composable
internal fun GitTreeItem(
    row: GitTreeRow,
    staged: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit
) {
    val folder = row.file == null
    Row(
        modifier = Modifier
            .treeRow(
                row,
                staged,
                collapsed,
                IntentScreen.secondaryText.copy(alpha = TREE_GUIDE_ALPHA)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                start = treeIndentation(row.depth),
                top = TreeRowPadding,
                bottom = TreeRowPadding
            ),
        verticalAlignment = if (folder) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(TreeRowSpacing)
    ) {
        Box(Modifier.size(TreeIconSize), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (folder) R.drawable.ic_folder else R.drawable.ic_file),
                contentDescription = null,
                tint = IntentScreen.secondaryText,
                modifier = Modifier.size(if (folder) TreeIconSize else TreeFileIconSize)
            )
        }
        TreeLabel(row, Modifier.weight(1f))
        if (folder) FolderTrail(row.count, collapsed)
    }
}

private fun Modifier.treeRow(
    row: GitTreeRow,
    staged: Boolean,
    collapsed: Boolean,
    guide: Color
): Modifier {
    val folder = row.file == null
    val stage = if (staged) "staged" else "unstaged"
    val base = fillMaxWidth()
        .heightIn(min = TreeRowHeight)
        .testTag("changes.${if (folder) "folder" else "file"}.$stage.${row.path}")
        .treeGuides(row.depth, guide, ownGuide = folder && !collapsed && row.depth < TREE_MAX_DEPTH)
    return if (!folder) {
        base
    } else {
        base.semantics {
            contentDescription = "Folder ${row.path}, ${row.count} files"
            stateDescription = if (collapsed) "Collapsed" else "Expanded"
        }
    }
}

@Composable
private fun TreeLabel(row: GitTreeRow, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                gitTreeDisplayName(row),
                style = treeNameStyle,
                color = IntentScreen.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            row.file?.metadata?.stats?.takeUnless { it.binary }?.let { stats ->
                Spacer(Modifier.weight(1f).width(8.dp))
                LineStats(stats.additions, stats.deletions)
            }
        }
        row.file?.let(::fileDetail)?.let { detail ->
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderTrail(count: Int, collapsed: Boolean) {
    Text(
        count.toString(),
        style = MaterialTheme.typography.bodySmall,
        color = IntentScreen.secondaryText
    )
    Icon(
        painterResource(if (collapsed) R.drawable.ic_chevron_right else R.drawable.ic_chevron_down),
        contentDescription = null,
        tint = IntentScreen.secondaryText,
        modifier = Modifier.size(TreeIconSize)
    )
}

@Composable
private fun LineStats(additions: Int, deletions: Int) {
    val dark = LocalIntentDarkTheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$additions additions, $deletions deletions"
        }
    ) {
        Text(
            "+$additions",
            style = MaterialTheme.typography.bodySmall,
            color = diffLineColor("Addition", dark)
        )
        Text(
            "−$deletions",
            style = MaterialTheme.typography.bodySmall,
            color = diffLineColor("Deletion", dark)
        )
    }
}

private fun fileDetail(file: GitFileStatus): String? = listOfNotNull(
    gitStatusLabel(file.status),
    file.metadata?.agentName,
    "Binary".takeIf { file.metadata?.stats?.binary == true }
).joinToString(" · ").ifEmpty { null }
