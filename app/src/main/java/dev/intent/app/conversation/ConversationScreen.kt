package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage

@Composable
fun ConversationRoute(
    store: ConversationStore,
    sends: ConversationSendStore,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    val sending by sends.state.collectAsState()
    LaunchedEffect(state.messages) { sends.observe(state.messages) }
    DisposableEffect(store) {
        store.start()
        sends.start()
        onDispose {
            store.stop()
            sends.stop()
        }
    }
    ConversationScreen(state, store::loadOlder, store::hydrate, {
        store.refresh()
        sends.refresh()
    }, modifier) {
        ConversationComposer(sending, { sends.send(it) }, sends::retract, sends::answer, {
            store.refresh()
            sends.refresh()
        })
    }
}

@Suppress("LongParameterList")
@Composable
fun ConversationScreen(
    state: ConversationState,
    onLoadOlder: () -> Unit,
    onHydrate: (String, String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    composer: @Composable () -> Unit = {}
) {
    val list = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }
    LaunchedEffect(state.messages.lastOrNull()) {
        if (followTail && state.messages.isNotEmpty()) list.scrollToItem(state.messages.lastIndex)
    }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Conversation", style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = onRefresh) { Text("Refresh") }
            TextButton(onClick = { followTail = !followTail }) {
                Text(if (followTail) "Pause auto-scroll" else "Follow latest")
            }
        }
        LaunchedEffect(followTail) {
            if (followTail &&
                state.messages.isNotEmpty()
            ) {
                list.scrollToItem(state.messages.lastIndex)
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.isLoading) CircularProgressIndicator()
        if (state.nextToken != null) {
            TextButton(onClick = {
                followTail = false
                onLoadOlder()
            }, enabled = !state.loadingOlder) {
                Text(if (state.loadingOlder) "Load in progress…" else "Load older messages")
            }
        }
        if (!state.isLoading && state.messages.isEmpty()) Text("No messages yet")
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                TranscriptMessage(message, state.hydrating, onHydrate)
            }
        }
        composer()
    }
}

@Composable
private fun TranscriptMessage(
    message: ConversationMessage,
    hydrating: Set<String>,
    onHydrate: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${message.role} · ${message.timestamp}",
            style = MaterialTheme.typography.labelMedium
        )
        message.contentBlocks.forEachIndexed { index, block ->
            androidx.compose.runtime.key(block.id ?: index) {
                TranscriptBlock(block, block.id in hydrating) {
                    block.id?.let { onHydrate(message.id, it) }
                }
            }
        }
        if (message.isStreaming == true) Text("Response in progress…")
        message.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message.metadata?.get("interruptReason")?.let { Text("Stopped: $it") }
        message.metadata?.get("finishReason")?.let { Text("Finished: $it") }
    }
}

@Composable
private fun TranscriptBlock(block: ContentBlock, hydrating: Boolean, onHydrate: () -> Unit) {
    if (block.isQuestion()) return
    var expanded by remember { mutableStateOf(false) }
    val truncated = block.needsHydration()
    val details = block.type in setOf("tool_use", "tool_result", "thinking")
    if (details) {
        TextButton(onClick = {
            expanded = !expanded
            if (expanded && truncated) onHydrate()
        }) {
            Text(
                "${if (expanded) "Hide" else "Show"} ${block.name ?: block.type.replace('_', ' ')}"
            )
        }
    }
    if (!details || expanded) {
        BlockContent(block)
        if (block.inputPruned == true ||
            block.outputPruned == true
        ) {
            Text("Full content is no longer available")
        }
        if (truncated) {
            TextButton(onClick = onHydrate, enabled = !hydrating && block.id != null) {
                Text(if (hydrating) "Load in progress…" else "Load full content")
            }
        }
    }
}

@Composable
private fun BlockContent(block: ContentBlock) {
    when (block.type) {
        "text", "thinking" -> Text(block.text.orEmpty())
        "tool_use" -> Text(block.input?.toString().orEmpty())
        "tool_result" -> Text(block.output?.toString() ?: block.text.orEmpty())
        "resource" -> Text(block.resource?.text ?: "Resource")
        else -> Text("${block.type}: ${block.fileName ?: "Content preview unavailable"}")
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ConversationPreview() {
    MaterialTheme {
        ConversationScreen(
            ConversationState(
                messages = listOf(
                    ConversationMessage(
                        "user",
                        "user",
                        listOf(ContentBlock("text", text = "Read the project files.")),
                        "12:00"
                    ),
                    ConversationMessage(
                        "reply",
                        "assistant",
                        listOf(
                            ContentBlock("tool_use", id = "reply:0", name = "read_file"),
                            ContentBlock(
                                "text",
                                id = "reply:1",
                                text = "The project contains an Android app."
                            )
                        ),
                        "12:01",
                        isStreaming = true
                    )
                ),
                isLoading = false
            ),
            {},
            { _, _ -> },
            {}
        )
    }
}

private fun ContentBlock.needsHydration(): Boolean =
    inputTruncated == true || outputTruncated == true || dataTruncated == true
