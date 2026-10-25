package dev.intent.app.comments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeBack
import dev.intent.core.protocol.models.Comment
import dev.intent.core.protocol.models.CommentThreadSummary

@Composable
fun CommentRoute(store: CommentStore, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by store.state.collectAsState()
    var selected by remember(store) { mutableStateOf<String?>(null) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    BackHandler {
        if (selected == null) onBack() else selected = null
    }
    Column(modifier) {
        CompositionLocalProvider(
            LocalChromeBack provides {
                if (selected == null) onBack() else selected = null
            }
        ) {
            CommentScreen(state, selected, {
                selected = it
            }, store::refresh, store::draft, store::reply)
        }
    }
}

@Suppress("LongParameterList") // Stateless Compose screen callbacks.
@Composable
fun CommentScreen(
    state: CommentState,
    selected: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onDraft: (String, String) -> Unit,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            CompactAppBar("Comments", actions = listOf(ChromeAction("Refresh", run = onRefresh)))
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val thread = state.threads.find { it.threadId == selected }
        when {
            selected == null -> {
                if (!state.loading && state.threads.isEmpty()) item { Text("No comment threads.") }
                items(state.threads, key = { it.threadId }) { row ->
                    TextButton(onClick = {
                        onSelect(row.threadId)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(row.targetedText ?: "Note comment")
                            Text("${row.commentCount} comments · ${row.status}")
                            Text("${row.latestCommentAuthor} · ${row.lastActivity}")
                        }
                    }
                }
            }
            thread == null -> item { Text("This thread is not available.") }
            else -> {
                item {
                    thread.targetedText?.let { Text("About: $it") }
                    Text("Status: ${thread.status}")
                }
                val comments = thread.comments
                if (comments ==
                    null
                ) {
                    item { Text("Comment text is not available. Refresh to load it.") }
                }
                items(comments.orEmpty(), key = { it.id }) { CommentBody(it) }
                item { ReplyComposer(state, selected, onDraft, onReply) }
            }
        }
    }
}

@Composable
private fun ReplyComposer(
    state: CommentState,
    threadId: String,
    onDraft: (String, String) -> Unit,
    onReply: (String) -> Unit
) {
    val sending = threadId in state.sending
    OutlinedTextField(
        value = state.drafts[threadId].orEmpty(),
        onValueChange = { onDraft(threadId, it) },
        label = { Text("Reply") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !sending
    )
    state.replyErrors[threadId]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = { onReply(threadId) },
        enabled =
        !sending && !state.drafts[threadId].isNullOrBlank()
    ) {
        Text(if (sending) "Sending…" else "Send reply")
    }
}

@Composable
private fun CommentBody(comment: Comment) {
    Column {
        Text(
            "${comment.author} · ${comment.createdAt}",
            style = MaterialTheme.typography.labelLarge
        )
        Text("${comment.type} · ${comment.status}", style = MaterialTheme.typography.labelSmall)
        Text(comment.content)
        comment.suggestionDiff?.let { Text("Suggestion: $it") }
        if (comment.isOrphaned == true) Text("The original note text is no longer available.")
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun CommentDetailPreview() {
    val comment =
        Comment(
            "root", "thread", "comment", "Can we clarify this step?",
            "Sam", "user", "open", "2026-10-24", "2026-10-24"
        )
    val thread =
        CommentThreadSummary(
            "thread", "spec", "open", "2026-10-24", "2026-10-24", "Sam", "user", "2026-10-24", 1,
            targetedText = "Read the plan",
            comments = listOf(
                comment
            )
        )
    MaterialTheme {
        CommentScreen(CommentState(threads = listOf(thread), loading = false), "thread", {
        }, {}, { _, _ -> }, {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun CommentListPreview() {
    MaterialTheme { CommentScreen(CommentState(loading = false), null, {}, {}, { _, _ -> }, {}) }
}
