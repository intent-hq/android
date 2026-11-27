package dev.intentapp.android.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus
import dev.intentapp.android.design.IntentCollapsibleSectionHeader
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.IntentTextButton as TextButton
import dev.intentapp.android.design.IntentTheme
import dev.intentapp.android.lists.RefreshableList
import dev.intentapp.android.navigation.WorkspaceSectionAppBar

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
    var hiddenSections by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val sections = GitSections(hiddenSections) { id ->
        hiddenSections = if (id in hiddenSections) hiddenSections - id else hiddenSections + id
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
                    gitStatusItems(state, sections, collapsed, onSelect) { key ->
                        collapsed = if (key in collapsed) collapsed - key else collapsed + key
                    }
                    gitCommitItems(state, sections, navigation.onCommit, onMore)
                }
            }
        }
    }
}

private class GitSections(val hidden: List<String>, val toggle: (String) -> Unit) {
    fun expanded(id: String) = id !in hidden
}

private fun LazyListScope.sectionHeader(
    sections: GitSections,
    id: String,
    title: String,
    count: Int?
) {
    item(key = "git.section.$id") {
        IntentCollapsibleSectionHeader(
            title = title,
            expanded = sections.expanded(id),
            onToggle = { sections.toggle(id) },
            count = count,
            modifier = Modifier.padding(top = IntentSpacing.medium).testTag("changes.section.$id")
        )
    }
}

private fun LazyListScope.gitStatusItems(
    state: GitState,
    sections: GitSections,
    collapsed: List<String>,
    onSelect: (GitFileStatus) -> Unit,
    onToggle: (String) -> Unit
) {
    state.error?.let {
        item(key = "git.error") {
            Text(it, color = IntentScreen.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (state.loading && state.status.files.isEmpty()) return
    for (staged in listOf(false, true)) {
        gitStageItems(state, sections, staged, collapsed, onSelect, onToggle)
    }
    if (state.status.filesTruncated) {
        item(key = "git.truncated") {
            SecondaryLine(
                "Some changed files could not be listed (${state.status.totalFiles ?: "unknown"} " +
                    "files). Open the desktop app to see all changes."
            )
        }
    } else if (!state.isClean() && state.error == null && state.status.files.isEmpty()) {
        item(key = "git.unavailable") {
            SecondaryLine("Changes are present, but no file rows are available. Refresh to retry.")
        }
    }
}

@Suppress("LongParameterList") // Lazy list section builder shares the screen's state and callbacks.
private fun LazyListScope.gitStageItems(
    state: GitState,
    sections: GitSections,
    staged: Boolean,
    collapsed: List<String>,
    onSelect: (GitFileStatus) -> Unit,
    onToggle: (String) -> Unit
) {
    val id = if (staged) "staged" else "unstaged"
    val title = if (staged) "Staged" else "Unstaged"
    val files = state.status.files.filter { it.staged == staged }
    sectionHeader(sections, id, title, files.size)
    when {
        !sections.expanded(id) -> Unit
        files.isEmpty() -> item(key = "git.empty.$id") {
            SecondaryLine("No ${title.lowercase()} files")
        }
        else -> {
            val hidden = collapsed.filter { it.startsWith("$staged:") }
                .map { it.substringAfter(':') }.toSet()
            items(gitTreeRows(files, hidden), key = { "$staged:${it.key}" }) { row ->
                val key = "$staged:${row.path}"
                GitTreeItem(row, staged, key in collapsed) {
                    val file = row.file
                    if (file != null) onSelect(file) else onToggle(key)
                }
            }
        }
    }
}

private fun LazyListScope.gitCommitItems(
    state: GitState,
    sections: GitSections,
    onCommit: (GitCommit) -> Unit,
    onMore: () -> Unit
) {
    item(key = "git.commits.spacer") { Spacer(Modifier.height(IntentSpacing.medium)) }
    sectionHeader(sections, "commits", "Commits", state.commits.size)
    if (!sections.expanded("commits")) return
    if (state.commits.isEmpty() && !state.loading && state.error == null) {
        item(key = "git.commits.empty") { SecondaryLine("No workspace commits yet") }
    }
    itemsIndexed(state.commits, key = { _, commit -> commit.hash }) { index, commit ->
        CommitRow(commit, divider = index < state.commits.lastIndex) { onCommit(commit) }
    }
    if (state.nextToken != null) {
        item(key = "git.commits.more") {
            TextButton(
                onClick = onMore,
                enabled = !state.loading && !state.loadingMore,
                modifier = Modifier.fillMaxWidth().padding(top = IntentSpacing.medium)
            ) {
                Text(if (state.loadingMore) "Loading…" else "Load more commits")
            }
        }
    }
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text,
        color = IntentScreen.secondaryText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = IntentSpacing.medium)
    )
}

@Composable
private fun CommitRow(commit: GitCommit, divider: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clickable(onClick = onClick)
            .padding(vertical = IntentSpacing.page),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.row)
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
                modifier = Modifier.weight(1f)
            )
            Text(
                commit.sha,
                fontFamily = FontFamily.Monospace,
                color = IntentScreen.secondaryText,
                style = MaterialTheme.typography.bodySmall
            )
            commitPushLabel(commit.isPushed)?.let {
                Text(
                    it,
                    color = IntentScreen.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    if (divider) HorizontalDivider(color = IntentScreen.text.copy(alpha = DIVIDER_ALPHA))
}

private const val DIVIDER_ALPHA = 0.1f

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
