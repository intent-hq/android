package dev.intent.app.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTheme
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.WorkspaceSectionAppBar
import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus

@Composable
fun GitScreen(
    state: GitState,
    onSelect: (GitFileStatus) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    navigation: GitNavigation = GitNavigation()
) {
    var collapsed by rememberSaveable(state.selectedRoot?.id) {
        mutableStateOf(emptyList<String>())
    }
    Column(Modifier.fillMaxSize()) {
        WorkspaceSectionAppBar("Changes")
        RefreshableList(state.loading, onRefresh) {
            if (state.loading && state.commits.isEmpty() && state.status.files.isEmpty()) {
                Spacer(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = IntentSpacing.page,
                        vertical = IntentSpacing.medium
                    )
                ) {
                    item(key = "git.repo") {
                        GitRepositoryPicker(state, navigation.onRoot)
                    }
                    gitStatusItems(state, collapsed, onSelect) { key ->
                        collapsed = if (key in collapsed) collapsed - key else collapsed + key
                    }
                    gitCommitItems(state, navigation.onCommit, onMore)
                }
            }
        }
    }
}

private fun LazyListScope.gitStatusItems(
    state: GitState,
    collapsed: List<String>,
    onSelect: (GitFileStatus) -> Unit,
    onToggle: (String) -> Unit
) {
    val hasStatus = !state.loading && state.error == null
    state.error?.let {
        item(key = "git.error") {
            Text(it, color = IntentScreen.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (state.status.filesTruncated) {
        item(key = "git.truncated") {
            Text(
                "The file list is incomplete (${state.status.totalFiles ?: "unknown"} files).",
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    if (state.isClean()) {
        item(key = "git.clean") {
            Text(
                "No uncommitted changes.",
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = IntentSpacing.content)
            )
        }
        return
    }
    for (staged in listOf(false, true)) {
        val files = state.status.files.filter { it.staged == staged }
        if (files.isNotEmpty()) {
            item(key = "git.section.$staged") {
                GitSectionHeading(if (staged) "Staged" else "Unstaged", files.size)
            }
            val hidden = collapsed.filter { it.startsWith("$staged:") }
                .map { it.substringAfter(':') }.toSet()
            val rows = gitTreeRows(files, hidden)
            items(rows, key = { "$staged:${it.key}" }) { row ->
                val key = "$staged:${row.path}"
                GitTreeItem(row, staged, key in collapsed) {
                    val file = row.file
                    if (file != null) onSelect(file) else onToggle(key)
                }
            }
        }
    }
    if (hasStatus && state.status.files.isEmpty() && !state.status.filesTruncated) {
        item(key = "git.unavailable") {
            Text(
                "Changes are present, but no file rows are available. Refresh to retry.",
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = IntentSpacing.row)
            )
        }
    }
}

private fun LazyListScope.gitCommitItems(
    state: GitState,
    onCommit: (GitCommit) -> Unit,
    onMore: () -> Unit
) {
    val hasStatus = !state.loading && state.error == null
    item(key = "git.commits.spacer") { Spacer(Modifier.height(20.dp)) }
    item(key = "git.commits.heading") { GitSectionHeading("Commit history") }
    if (state.commits.isEmpty() && hasStatus) {
        item(key = "git.commits.empty") {
            Text(
                "No commits yet.",
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = IntentSpacing.medium)
            )
        }
    }
    items(state.commits, key = { it.hash }) { commit ->
        CommitRow(commit) { onCommit(commit) }
    }
    if (state.nextToken != null) {
        item(key = "git.commits.more") {
            TextButton(
                onClick = onMore,
                enabled = !state.loading && !state.loadingMore
            ) {
                Text(if (state.loadingMore) "Loading…" else "Load older commits")
            }
        }
    }
}

@Composable
private fun GitSectionHeading(title: String, count: Int? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = IntentSpacing.content, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = IntentScreen.secondaryText
        )
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = IntentScreen.secondaryText
            )
        }
    }
}

@Composable
private fun CommitRow(commit: GitCommit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = IntentSpacing.row),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        Text(
            commit.message.lineSequence().firstOrNull().orEmpty().ifBlank { "Untitled commit" },
            style = MaterialTheme.typography.titleSmall,
            color = IntentScreen.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
            Text(
                commit.author.ifBlank { "Unknown author" },
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                commit.sha,
                fontFamily = FontFamily.Monospace,
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                commitDate(commit.date),
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        commitPushLabel(commit.isPushed)?.let {
            Text(
                it,
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Preview
@Composable
fun GitScreenPreview() {
    IntentTheme {
        GitScreen(
            GitState(
                status = GitStatus(
                    branch = "main",
                    files = listOf(GitFileStatus("app.kt", "M", false))
                ),
                loading = false
            ),
            {},
            {},
            {}
        )
    }
}
