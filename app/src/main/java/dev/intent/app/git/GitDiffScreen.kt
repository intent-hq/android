package dev.intent.app.git

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentScreen
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.models.GitDiffLine
import dev.intent.core.protocol.models.GitFileStatus

@Composable
fun GitDiffScreen(state: GitState, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CompactAppBar(
            state.selected?.path.orEmpty(),
            actions = listOf(ChromeAction("Refresh diff", run = onRetry))
        )
        LazyColumn(
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(if (state.selected?.staged == true) "Staged diff" else "Unstaged diff")
                if (state.diffLoading) CircularProgressIndicator()
                state.diffError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            val hunks = state.diffs.flatMap { it.hunks }
            if (!state.diffLoading && state.diffError == null && hunks.isEmpty()) {
                item {
                    Text(
                        "No text diff is available. The file can be binary, too large, untracked, or unchanged."
                    )
                }
            }
            hunks.forEach { hunk ->
                item {
                    Text(
                        "@@ -${hunk.oldStart},${hunk.oldLines} +${hunk.newStart},${hunk.newLines} @@",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                items(hunk.lines) { line -> DiffLine(line) }
            }
        }
    }
}

@Composable
internal fun DiffLine(line: GitDiffLine) {
    val (prefix, color) = when (line.type) {
        "Addition" -> "+" to AdditionColor
        "Deletion" -> "-" to MaterialTheme.colorScheme.error
        else -> " " to MaterialTheme.colorScheme.onSurface
    }
    Text(
        "${line.oldNumber ?: ""}\t${line.newNumber ?: ""}\t$prefix${line.content}",
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        fontFamily = FontFamily.Monospace,
        color = color
    )
}

@Preview
@Composable
fun GitDiffPreview() {
    MaterialTheme {
        GitDiffScreen(GitState(selected = GitFileStatus("app.kt", "M", false), loading = false)) {}
    }
}

private const val ADDITION_COLOR_ARGB = 0xFF25843B
private val AdditionColor = Color(ADDITION_COLOR_ARGB)
