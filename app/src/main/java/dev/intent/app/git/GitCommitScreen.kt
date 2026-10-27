package dev.intent.app.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitCommitFile

@Composable
internal fun GitCommitScreen(
    state: GitState,
    onRetry: (GitCommit) -> Unit,
    onFile: (GitCommitFile) -> Unit
) {
    val commit = state.selectedCommit ?: return
    Column(Modifier.fillMaxSize()) {
        CompactAppBar("Commit changes")
        LazyColumn(
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            item {
                Text(commit.message)
                Text("${commit.author} · ${commit.sha}", color = IntentScreen.secondaryText)
            }
            item {
                if (state.commitLoading) CircularProgressIndicator()
                state.commitError?.let {
                    Text(it, color = IntentScreen.error)
                    TextButton(onClick = { onRetry(commit) }) { Text("Retry commit") }
                }
                if (!state.commitLoading &&
                    state.commitError == null &&
                    state.commitFiles.isEmpty()
                ) {
                    Text("No changed files")
                }
            }
            if (state.commitFiles.isNotEmpty()) {
                item { CommitFilePicker(state, onFile) }
                item { CommitDiffStatus(state, onFile) }
                state.diffs.flatMap { it.hunks }.forEach { hunk ->
                    item {
                        Text(
                            "@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    items(hunk.lines) { DiffLine(it) }
                }
            }
        }
    }
}

@Composable
private fun CommitFilePicker(state: GitState, onFile: (GitCommitFile) -> Unit) {
    var expanded by remember(state.selectedCommit?.hash) { mutableStateOf(false) }
    Text("File (${state.commitFiles.size})")
    Box {
        TextButton(onClick = { expanded = true }) { Text(state.selected?.path.orEmpty()) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            state.commitFiles.forEach { file ->
                DropdownMenuItem(text = {
                    Text("${file.path} (+${file.additions} −${file.deletions})")
                }, onClick = {
                    expanded = false
                    onFile(file)
                })
            }
        }
    }
}

@Composable
private fun CommitDiffStatus(state: GitState, onFile: (GitCommitFile) -> Unit) {
    if (state.diffLoading) CircularProgressIndicator()
    state.diffError?.let {
        Text(it, color = IntentScreen.error)
        TextButton(onClick = {
            state.commitFiles.firstOrNull { it.path == state.selected?.path }?.let(onFile)
        }) { Text("Retry diff") }
    }
    if (!state.diffLoading && state.diffError == null && state.diffs.all { it.hunks.isEmpty() }) {
        Text("No text changes")
    }
}
