package dev.intent.app.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.attachments.MediaPreview
import dev.intent.app.attachments.MediaReference
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.markdown.MarkdownContent
import dev.intent.core.protocol.attachments.messageBlock
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage

@Suppress("LongParameterList", "LongMethod") // Wires transcript, drafts, seen state, and controls.
@Composable
fun ConversationRoute(
    store: ConversationStore,
    sends: ConversationSendStore,
    modifier: Modifier = Modifier,
    photos: PhotoAttachmentStore? = null,
    draft: ComposerDraft = remember(store) { ComposerDraft() },
    seen: ConversationSeen? = null,
    statusStrip: @Composable (Boolean) -> Unit = {}
) {
    val state by store.state.collectAsState()
    val sending by sends.state.collectAsState()
    val questionPhotos = remember(photos, sending.questions?.messageId) {
        sending.questions?.let { photos?.independent() }
    }
    DisposableEffect(questionPhotos) { onDispose { questionPhotos?.close() } }
    LaunchedEffect(state.messages) { sends.observe(state.messages) }
    DisposableEffect(store) {
        store.start()
        sends.start()
        onDispose {
            store.stop()
            sends.stop()
        }
    }
    val agent = dev.intent.app.agents.LocalConversationAgent.current
    val proposals = dev.intent.app.assistant.LocalProposalStore.current
    LaunchedEffect(proposals, state.messages, agent?.metadata, sending.checking) {
        if (!sending.checking &&
            state.messages.any { message ->
                message.contentBlocks.any {
                    dev.intent.app.assistant.ActionProposal.parse(it) !=
                        null
                }
            }
        ) {
            proposals?.refresh()
        }
    }
    val streaming = state.isStreaming ||
        (state.isLoading && agent?.conversationRunning() == true)
    ConversationSeenEffect(
        seen,
        state.copy(isStreaming = streaming),
        agent?.metadata?.string("lastSeenMessageId")
    )
    LaunchedEffect(streaming) { sends.observeRunning(streaming) }
    ConversationScreen(
        state.copy(isStreaming = streaming),
        store::loadOlder,
        store::hydrate,
        {
            store.refresh()
            sends.refresh()
            proposals?.refresh()
        },
        modifier,
        onSuggestedPrompt = sends::sendSuggestedPrompt,
        suggestionsEnabled = sending.attempts.isEmpty()
    ) {
        statusStrip(streaming)
        ConversationComposer(
            sending, { text ->
                val ready = photos?.state?.value
                if (ready?.ready == false) {
                    false
                } else {
                    val confirmed = draft.confirmation()
                    val accepted = sends.send(
                        text,
                        images = ready?.photos.orEmpty().filter {
                            it.description?.image == true
                        }.mapNotNull {
                            it.placed?.messageBlock(true)
                        },
                        files = ready?.photos.orEmpty().filter {
                            it.description?.image != true
                        }.mapNotNull {
                            it.placed?.messageBlock()
                        },
                        onConfirmed = {
                            confirmed()
                            seen?.sentMessage()
                        }
                    )
                    if (accepted) photos?.consume()
                    accepted
                }
            }, sends::retract, { id, answers ->
                val ready = questionPhotos?.state?.value
                if (ready?.ready == false) {
                    false
                } else {
                    val accepted = sends.answer(
                        id,
                        answers,
                        images = ready?.photos.orEmpty().filter { it.description?.image == true }
                            .mapNotNull { it.placed?.messageBlock(true) },
                        files = ready?.photos.orEmpty().filter { it.description?.image != true }
                            .mapNotNull { it.placed?.messageBlock() }
                    )
                    if (accepted) questionPhotos?.consume()
                    accepted
                }
            }, photos, draft, sends::retry, sends::refresh,
            clearOnSend = false,
            onStop = sends::stopReply,
            onEditQueued = sends::editQueued,
            onSendNow = sends::sendNow,
            onDismissQuestions = sends::dismissQuestions,
            questionPhotos = questionPhotos
        )
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
    onSuggestedPrompt: (String) -> Boolean = { false },
    suggestionsEnabled: Boolean = true,
    composer: @Composable () -> Unit = {}
) {
    Column(
        modifier.padding(horizontal = dev.intent.app.design.IntentSpacing.page, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConversationTranscript(
            state,
            onLoadOlder,
            onHydrate,
            onRefresh,
            Modifier.weight(1f).then(dev.intent.app.drawers.LocalTranscriptDrawerGesture.current),
            onSuggestedPrompt,
            suggestionsEnabled
        )
        composer()
    }
}

@Suppress("LongParameterList")
@Composable
internal fun TranscriptMessage(
    row: TranscriptRow,
    hydrating: Set<String>,
    onHydrate: (String, String) -> Unit,
    onSuggestedPrompt: (String) -> Boolean,
    suggestionsEnabled: Boolean,
    showSuggestions: Boolean
) {
    val message = row.messages.first()
    val activity = message.isToolActivity()
    val notice = remember(message) { ConversationNoise.label(message) }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(row.id) {
        mutableStateOf(false)
    }
    Column {
        if (activity || notice != null) {
            val label = if (activity) {
                toolActivityLabel(row.messages.flatMap { it.contentBlocks })
            } else {
                "$notice${if (row.messages.size > 1) " · ${row.messages.size} updates" else ""}"
            }
            CompactDisclosure(label, expanded) { expanded = !expanded }
        }
        if ((!activity && notice == null) || expanded) {
            val messages = if (activity) {
                row.messages
            } else {
                row.messages.distinctBy {
                    it.contentBlocks
                }
            }
            messages.forEach {
                androidx.compose.runtime.key(it.id) {
                    if (activity) {
                        TranscriptMetadata(it)
                        ToolActivityBlocks(it.contentBlocks, hydrating) { block ->
                            block.id?.let { id -> onHydrate(it.id, id) }
                        }
                    } else {
                        TranscriptMessageContents(
                            it,
                            hydrating,
                            onHydrate,
                            onSuggestedPrompt,
                            suggestionsEnabled,
                            showSuggestions
                        )
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList", "LongMethod") // Transcript sections and scoped proposal context.
@Composable
private fun TranscriptMessageContents(
    message: ConversationMessage,
    hydrating: Set<String>,
    onHydrate: (String, String) -> Unit,
    onSuggestedPrompt: (String) -> Boolean,
    suggestionsEnabled: Boolean,
    showSuggestions: Boolean
) {
    val presentation = remember(message.contentBlocks, message.isStreaming) {
        if (message.role == "assistant") {
            SuggestedPrompts.parse(message.contentBlocks, message.isStreaming == true)
        } else {
            PromptPresentation(message.contentBlocks, emptyList())
        }
    }
    var promptSent by androidx.compose.runtime.saveable.rememberSaveable(message.id) {
        mutableStateOf(false)
    }
    val highlight = if (message.role == "user") {
        Modifier.fillMaxWidth().testTag("user-message-${message.id}")
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.shapes.extraLarge
            )
            .padding(12.dp)
    } else {
        Modifier
    }
    Column(highlight, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TranscriptMetadata(message)
        val sections = remember(presentation.blocks, message.isStreaming) {
            if (message.role == "assistant") {
                groupToolSections(
                    transcriptSections(
                        presentation.blocks,
                        message.isStreaming == true
                    )
                )
            } else {
                presentation.blocks.mapIndexed { index, block ->
                    TranscriptSection("block:$index", blocks = listOf(block))
                }
            }
        }
        sections.forEach { section ->
            androidx.compose.runtime.key(section.id) {
                androidx.compose.runtime.CompositionLocalProvider(
                    dev.intent.app.assistant.LocalProposalMessage provides message.id
                ) {
                    TranscriptSectionContent(
                        section,
                        section == sections.last(),
                        hydrating
                    ) { block ->
                        block.id?.let { onHydrate(message.id, it) }
                    }
                }
            }
        }
        if (showSuggestions && !promptSent) {
            PromptChips(presentation.prompts, suggestionsEnabled) { prompt ->
                if (!promptSent) promptSent = onSuggestedPrompt(prompt)
            }
        }
        message.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message.metadata?.get("interruptReason")?.let { Text("Stopped: $it") }
        message.metadata?.get("finishReason")?.let { Text("Finished: $it") }
    }
}

@Composable
internal fun TranscriptBlock(block: ContentBlock, hydrating: Boolean, onHydrate: () -> Unit) {
    if (block.isQuestion() || block.type == "thinking") return
    var expanded by remember { mutableStateOf(false) }
    val truncated = block.needsHydration()
    val details = block.type in setOf("tool_use", "tool_result")
    if (details) {
        CompactDisclosure(block.name ?: block.type.replace('_', ' '), expanded) {
            expanded = !expanded
            if (expanded && truncated) onHydrate()
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
        "text" -> TranscriptText(block.text.orEmpty())
        "tool_use", "tool_result" -> Text(
            toolBody(block),
            style = MaterialTheme.typography.bodySmall
        )
        "image", "file" -> MediaPreview(
            MediaReference(
                block.fileName ?: if (block.type == "image") "Image" else "Attachment",
                block.type == "image",
                block.attachmentId,
                data = block.data,
                mimeType = block.mimeType
            )
        )
        "resource" -> {
            val proposal = remember(block) { dev.intent.app.assistant.ActionProposal.parse(block) }
            if (proposal != null) {
                dev.intent.app.assistant.ProposalCard(proposal)
            } else {
                MarkdownContent(block.resource?.text ?: "Resource")
            }
        }
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

@Composable
internal fun CompactDisclosure(label: String, expanded: Boolean, onClick: () -> Unit) {
    Text(
        "${if (expanded) "▾" else "▸"} $label",
        Modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TranscriptText(text: String) {
    val parts = remember(text) { transcriptTextParts(text) }
    parts.forEachIndexed { index, part ->
        androidx.compose.runtime.key(index) {
            if (part.toolMarkup) {
                var expanded by remember { mutableStateOf(false) }
                CompactDisclosure("Tool detail", expanded) { expanded = !expanded }
                if (expanded) {
                    Text(
                        part.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (part.text.isNotBlank()) {
                MarkdownContent(part.text)
            }
        }
    }
}
