package dev.intent.app.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField
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
                CommitMetadata(commit)
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
                    item { DiffHunk(hunk) }
                }
            }
        }
    }
}

@Suppress("DEPRECATION") // Same clipboard API as existing connection details.
@Composable
private fun CommitMetadata(commit: GitCommit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(commit.hash) { mutableStateOf(false) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)) {
            Text(commit.message)
            Text(commit.author.ifBlank { "Unknown author" }, color = IntentScreen.secondaryText)
            Text(commitDateTime(commit.date), color = IntentScreen.secondaryText)
            commitPushLabel(commit.isPushed)?.let { Text(it, color = IntentScreen.secondaryText) }
            Text(commit.hash, fontFamily = FontFamily.Monospace)
        }
    }
    TextButton(
        onClick = {
            clipboard.setText(AnnotatedString(commit.hash))
            copied = true
        },
        modifier = Modifier.testTag("commit.copy")
    ) {
        Text(if (copied) "Copied commit hash" else "Copy full commit hash")
    }
}

@Composable
private fun CommitFilePicker(state: GitState, onFile: (GitCommitFile) -> Unit) {
    var expanded by remember(state.selectedCommit?.hash) { mutableStateOf(false) }
    Text("File (${state.commitFiles.size})")
    TextButton(onClick = { expanded = true }) {
        Text(state.selected?.path ?: "Choose a file")
    }
    if (expanded) {
        CommitFileDialog(state.commitFiles, onDismiss = { expanded = false }) {
            expanded = false
            onFile(it)
        }
    }
}

@Composable
private fun CommitFileDialog(
    files: List<GitCommitFile>,
    onDismiss: () -> Unit,
    onFile: (GitCommitFile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(files, query) { filterCommitFiles(files, query) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = IntentScreen.surface) {
            Column(
                Modifier.fillMaxWidth().heightIn(
                    max = FILE_PICKER_MAX_HEIGHT.dp
                ).padding(IntentScreen.pageInsets),
                verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
            ) {
                Text("Changed files")
                IntentTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Search changed files") },
                    singleLine = true
                )
                if (matches.isEmpty()) Text("No matching files")
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(matches, key = { it.path }) { file ->
                        TextButton(onClick = { onFile(file) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${file.path} (+${file.additions} −${file.deletions})")
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close file list") }
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

private const val FILE_PICKER_MAX_HEIGHT = 520
