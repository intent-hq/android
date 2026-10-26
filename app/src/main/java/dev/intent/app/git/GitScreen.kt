package dev.intent.app.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.WorkspaceSectionAppBar
import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus

@Suppress("CyclomaticComplexMethod", "LongMethod") // Declarative list sections and loading states.
@Composable
fun GitScreen(
    state: GitState,
    onSelect: (GitFileStatus) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    repository: String? = LocalWorkspaceRepository.current
) {
    val hasStatus = !state.loading && state.error == null
    Column(Modifier.fillMaxSize()) {
        WorkspaceSectionAppBar("Changes")
        RefreshableList(state.loading, onRefresh) {
            if (state.loading && state.commits.isEmpty() && state.status.files.isEmpty()) {
                Spacer(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = IntentScreen.pageInsets,
                    verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
                ) {
                    item {
                        IntentSection("Repository") {
                            Text(
                                repository ?: "Repository unavailable",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                state.status.branch ?: "No branch",
                                color = IntentScreen.secondaryText
                            )
                        }
                    }
                    item {
                        state.error?.let { Text(it, color = IntentScreen.error) }
                        if (state.status.filesTruncated) {
                            Text(
                                "The file list is incomplete " +
                                    "(${state.status.totalFiles ?: "unknown"} files)."
                            )
                        }
                    }
                    if (state.isClean()) {
                        item {
                            IntentSection("Working tree clean") {
                                Text("No uncommitted changes.", color = IntentScreen.secondaryText)
                            }
                        }
                    } else {
                        for (staged in listOf(true, false)) {
                            val files = state.status.files.filter { it.staged == staged }
                            if (files.isNotEmpty()) {
                                item {
                                    Text(
                                        if (staged) "Staged" else "Unstaged",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                items(files) { file ->
                                    TextButton(onClick = {
                                        onSelect(file)
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("${file.status}  ${file.path}")
                                    }
                                }
                            }
                        }
                        if (hasStatus &&
                            state.status.files.isEmpty() &&
                            !state.status.filesTruncated
                        ) {
                            item {
                                Text(
                                    "Changes are present, but no file rows are available. " +
                                        "Refresh to retry."
                                )
                            }
                        }
                    }
                    item { Text("Commit history", style = MaterialTheme.typography.titleMedium) }
                    if (state.commits.isEmpty() && hasStatus) {
                        item { Text("No commits yet.", color = IntentScreen.secondaryText) }
                    }
                    items(state.commits, key = { it.hash }) { commit -> CommitRow(commit) }
                    if (state.nextToken != null) {
                        item {
                            TextButton(
                                onClick = onMore,
                                enabled = !state.loading && !state.loadingMore
                            ) {
                                Text(if (state.loadingMore) "Loading…" else "Load older commits")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitRow(commit: GitCommit) {
    IntentSection(
        commit.message.lineSequence().firstOrNull().orEmpty().ifBlank {
            "Untitled commit"
        }
    ) {
        Text(
            commit.author.ifBlank { "Unknown author" },
            color = IntentScreen.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)) {
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
    }
}

@Preview
@Composable
fun GitScreenPreview() {
    MaterialTheme {
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
