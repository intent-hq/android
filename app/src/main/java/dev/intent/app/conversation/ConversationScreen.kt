package dev.intent.app.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import dev.intent.app.agents.LocalConversationAgent
import dev.intent.app.attachments.MediaPreview
import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.markdown.MarkdownContent
import dev.intent.core.protocol.attachments.messageBlock
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage

@Composable
fun ConversationRoute(
    store: ConversationStore,
    sends: ConversationSendStore,
    modifier: Modifier = Modifier,
    photos: PhotoAttachmentStore? = null
) {
    val draft = remember(store) { ComposerDraft() }
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
    }, modifier, onSuggestedPrompt = { draft.text = it }) {
        ConversationComposer(sending, { text ->
            val ready = photos?.state?.value
            if (ready?.ready == false) {
                false
            } else {
                val accepted = sends.send(
                    text,
                    images = ready?.photos.orEmpty().mapNotNull {
                        it.placed?.messageBlock(true)
                    }
                )
                if (accepted) photos?.consume()
                accepted
            }
        }, sends::retract, sends::answer, photos, draft)
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
    onSuggestedPrompt: (String) -> Unit = {},
    composer: @Composable () -> Unit = {}
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConversationTranscript(
            state,
            onLoadOlder,
            onHydrate,
            onRefresh,
            Modifier.weight(1f),
            onSuggestedPrompt
        )
        composer()
    }
}

@Composable
internal fun TranscriptMessage(
    message: ConversationMessage,
    hydrating: Set<String>,
    onHydrate: (String, String) -> Unit,
    onSuggestedPrompt: (String) -> Unit
) {
    val notice = remember(message) { ConversationNoise.label(message) }
    var expanded by remember(message.id) { mutableStateOf(false) }
    Column {
        if (notice != null) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    "${if (expanded) "▾" else "▸"} $notice",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (notice == null || expanded) {
            TranscriptMessageContents(message, hydrating, onHydrate, onSuggestedPrompt)
        }
    }
}

@Composable
private fun TranscriptMessageContents(
    message: ConversationMessage,
    hydrating: Set<String>,
    onHydrate: (String, String) -> Unit,
    onSuggestedPrompt: (String) -> Unit
) {
    val presentation = remember(message.contentBlocks, message.isStreaming) {
        if (message.role == "assistant") {
            SuggestedPrompts.parse(message.contentBlocks, message.isStreaming == true)
        } else {
            PromptPresentation(message.contentBlocks, emptyList())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val agent = LocalConversationAgent.current?.takeIf {
            message.agentId == null || message.agentId == it.id
        }
        val author = if (message.role == "assistant") agent?.name ?: message.role else message.role
        Text(
            "$author · ${message.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        presentation.blocks.forEachIndexed { index, block ->
            androidx.compose.runtime.key(block.id ?: index) {
                TranscriptBlock(block, block.id in hydrating) {
                    block.id?.let { onHydrate(message.id, it) }
                }
            }
        }
        presentation.prompts.forEach { prompt ->
            SuggestionChip(onClick = {
                onSuggestedPrompt(prompt)
            }, label = { Text(PromptLinks.visible(prompt)) })
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
                "${if (expanded) "▾" else "▸"} ${block.name ?: block.type.replace('_', ' ')}"
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
        "text", "thinking" -> MarkdownContent(block.text.orEmpty())
        "tool_use" -> Text(block.input?.toString().orEmpty())
        "tool_result" -> Text(block.output?.toString() ?: block.text.orEmpty())
        "image", "file" -> MediaPreview(
            MediaReference(
                block.fileName ?: if (block.type == "image") "Image" else "Attachment",
                block.type == "image",
                block.attachmentId,
                data = block.data,
                mimeType = block.mimeType
            )
        )
        "resource" -> MarkdownContent(block.resource?.text ?: "Resource")
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
