package dev.intent.app.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitStatus

@Suppress("CyclomaticComplexMethod") // Declarative list sections and loading states.
@Composable
fun GitScreen(
    state: GitState,
    onSelect: (GitFileStatus) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit
) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Git changes", style = MaterialTheme.typography.headlineMedium)
            Text(state.status.branch ?: "No branch")
            TextButton(onClick = onRefresh) { Text("Refresh") }
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.status.filesTruncated) {
                Text(
                    "The file list is incomplete (${state.status.totalFiles ?: "unknown"} files)."
                )
            }
        }
        for (staged in listOf(true, false)) {
            item {
                Text(
                    if (staged) "Staged" else "Unstaged",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            val files = state.status.files.filter { it.staged == staged }
            if (files.isEmpty() && !state.loading) item { Text("No files.") }
            items(files) { file ->
                TextButton(onClick = { onSelect(file) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${file.status}  ${file.path}")
                }
            }
        }
        item { Text("Commit history", style = MaterialTheme.typography.titleMedium) }
        if (state.commits.isEmpty() && !state.loading) item { Text("No commits.") }
        items(state.commits, key = { it.hash }) { commit ->
            Text(commit.message, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${commit.sha} · ${commit.author} · ${commit.date}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.nextToken != null) {
            item {
                TextButton(onClick = onMore, enabled = !state.loading && !state.loadingMore) {
                    Text(if (state.loadingMore) "Loading…" else "Load older commits")
                }
            }
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
