package dev.intent.app.conversation

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.CommandResult
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.QueueResult
import dev.intent.core.protocol.models.QueuedMessage
import dev.intent.core.protocol.models.ResourceContents
import dev.intent.core.protocol.models.SendResult
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSendStoreTest {
    private val commands = FakeCommands()
    private val store = ConversationSendStore(commands, "agent")

    @Test
    fun draftClearsOnlyAfterConfirmedSendAndEchoClearsAfterUncertainReply() {
        val draft = ComposerDraft("keep me")
        store.send(draft.text, onConfirmed = draft.confirmation())
        assertEquals("keep me", draft.text)
        val sent = commands.sent.single()
        sent.result.completeExceptionally(IllegalStateException("timeout"))
        assertEquals("keep me", draft.text)
        store.observe(listOf(ConversationMessage("echo", "user", appMessageId = sent.id)))
        assertEquals("", draft.text)
    }

    @Test
    fun confirmedSendPreservesTextEditedWhileRequestWasPending() {
        val draft = ComposerDraft("first")
        store.send(draft.text, onConfirmed = draft.confirmation())
        draft.text = "second"
        commands.sent.single().result.complete(SendResult(true, false, messageId = "confirmed"))
        assertEquals("second", draft.text)
    }

    @Test
    fun suggestionSendsOnlyPromptAndRejectsDuplicateUntilReconciled() {
        val draft = ComposerDraft().apply { text = "Unfinished draft" }
        assertTrue(store.sendSuggestedPrompt("Run tests"))
        assertFalse(store.sendSuggestedPrompt("Run tests"))
        val sent = commands.sent.single()
        assertEquals("Run tests", sent.content)
        assertNull(sent.metadata)
        assertTrue(commands.images.isEmpty())
        assertTrue(store.state.value.attempts.single().files.isEmpty())
        assertEquals("Unfinished draft", draft.text)
        sent.result.completeExceptionally(IllegalStateException("timeout"))
        assertFalse(store.sendSuggestedPrompt("Run tests"))
        assertEquals(SendStatus.UNCONFIRMED, store.state.value.attempts.single().status)
        store.observe(listOf(ConversationMessage("echo", "user", appMessageId = sent.id)))
        assertTrue(store.sendSuggestedPrompt("Review code"))
    }

    @Test
    fun sendsOnceAndEchoWinsOverLateFailure() {
        store.send("Hello")
        val sent = commands.sent.single()
        assertEquals("Hello", sent.content)
        store.observe(listOf(ConversationMessage("canonical", "user", appMessageId = sent.id)))
        sent.result.completeExceptionally(IllegalStateException("timeout"))
        assertTrue(store.state.value.attempts.isEmpty())
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun reconnectReconcilesUnconfirmedSendWithoutReplaying() {
        store.start()
        store.send("Hello")
        val sent = commands.sent.single()
        sent.result.completeExceptionally(IllegalStateException("disconnected"))
        assertEquals(SendStatus.UNCONFIRMED, store.state.value.attempts.single().status)
        commands.rows = listOf(ConversationMessage("server", "user", appMessageId = sent.id))
        commands.refresh()
        assertTrue(store.state.value.attempts.isEmpty())
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun absentRowsAndUnknownCorrelationNeverProveNonDelivery() {
        store.send("Hello")
        commands.sent.single().result.completeExceptionally(IllegalStateException("timeout"))
        store.observe(
            listOf(ConversationMessage(commands.sent.single().id, "user", appMessageId = "foreign"))
        )
        store.refresh()
        assertEquals(SendStatus.UNCONFIRMED, store.state.value.attempts.single().status)
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun queuedSendRetractsOnlyAfterAcknowledgement() {
        store.send("Later")
        val sent = commands.sent.single()
        val queued =
            QueuedMessage(
                "q",
                "Later",
                "now",
                0,
                buildJsonObject {
                    put("userAppMessageId", sent.id)
                }
            )
        commands.queued = listOf(queued)
        sent.result.complete(SendResult(true, true, queuedMessage = queued))
        assertEquals(SendStatus.QUEUED, store.state.value.attempts.single().status)
        store.retract("q")
        assertEquals("q", store.state.value.queue.single().id)
        commands.queued = emptyList()
        commands.removals.single().complete(CommandResult(true))
        assertTrue(store.state.value.queue.isEmpty())
        assertTrue(store.state.value.attempts.isEmpty())
    }

    @Test
    fun questionAnswerUsesTagAndReappearsAfterQueueRetraction() {
        commands.details =
            Agent(
                "agent",
                "Agent",
                metadata = buildJsonObject {
                    put("pendingQuestionsMessageId", "questions")
                }
            )
        commands.rows = listOf(questionRow())
        store.refresh()
        assertEquals("questions", store.state.value.questions?.messageId)
        assertTrue(store.answer("questions", mapOf("q" to QuestionAnswer(listOf("One"), "custom"))))
        val sent = commands.sent.single()
        assertEquals("Q: Choose?\nA: One, (Other) custom", sent.content)
        assertEquals("questions", sent.metadata.answeredQuestion())
        assertFalse(store.answer("questions", emptyMap()))
        val queued = QueuedMessage(
            "q-row",
            sent.content,
            "now",
            0,
            buildJsonObject {
                put("type", "question_answers")
                put("answeredQuestionsMessageId", "questions")
                put("userAppMessageId", sent.id)
            }
        )
        commands.queued = listOf(queued)
        sent.result.complete(SendResult(true, true, queuedMessage = queued))
        assertNull(store.state.value.questions)
        store.retract("q-row")
        commands.queued = emptyList()
        commands.removals.single().complete(CommandResult(true))
        assertEquals("questions", store.state.value.questions?.messageId)
    }

    @Test
    fun explicitClearedMarkerAndStreamingRowsDoNotShowQuestions() {
        commands.rows = listOf(questionRow())
        commands.details =
            Agent(
                "agent",
                "Agent",
                metadata = buildJsonObject {
                    put("pendingQuestionsMessageId", "")
                }
            )
        store.refresh()
        assertNull(store.state.value.questions)
        commands.details = Agent("agent", "Agent")
        commands.rows = listOf(questionRow().copy(isStreaming = true))
        store.refresh()
        assertNull(store.state.value.questions)
    }

    @Test
    fun staleReadAndClosedMutationCannotOverwriteState() {
        val old = CompletableFuture<QueueResult>()
        commands.queueReply = old
        store.refresh()
        commands.queueReply = null
        commands.queued = listOf(QueuedMessage("new", "new", "now", 0))
        store.refresh()
        old.complete(QueueResult(true, emptyList()))
        assertEquals("new", store.state.value.queue.single().id)
        store.send("Hello")
        store.close()
        val before = store.state.value
        commands.sent.single().result.complete(SendResult(true, false))
        assertEquals(before, store.state.value)
    }

    @Test
    fun attachmentOnlySendKeepsReferencesAcrossUnconfirmedDelivery() {
        val image = buildJsonObject {
            put("type", "image")
            put("attachmentId", "photo")
        }
        assertTrue(store.send("", images = listOf(image)))
        assertEquals(listOf(image), commands.images)
        commands.sent.single().result.completeExceptionally(IllegalStateException("lost"))
        assertEquals(listOf(image), store.state.value.attempts.single().images)
        store.refresh()
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun onlyLocalPreDispatchFailureAllowsRetryWithSameIdentityAndAttachments() {
        val image = buildJsonObject { put("attachmentId", "photo") }
        store.send("Hello", images = listOf(image))
        val sent = commands.sent.single()
        assertEquals(SendStatus.SENDING, store.state.value.attempts.single().status)
        sent.result.completeExceptionally(
            java.util.concurrent.CompletionException(
                dev.intent.core.protocol.session.SessionDisconnectedException(requestNotSent = true)
            )
        )
        assertEquals(SendStatus.NOT_SENT, store.state.value.attempts.single().status)
        store.retry(sent.id)
        assertEquals(SendStatus.SENDING, store.state.value.attempts.single().status)
        assertEquals(sent.id, commands.sent.last().id)
        assertEquals(listOf(image), commands.images)
        store.retry(sent.id)
        assertEquals(2, commands.sent.size)
        commands.sent.last().result.complete(SendResult(true, false, messageId = "canonical"))
        assertEquals(SendStatus.ACCEPTED, store.state.value.attempts.single().status)
        store.observe(listOf(ConversationMessage("canonical", "user", appMessageId = sent.id)))
        assertTrue(store.state.value.attempts.isEmpty())
    }

    @Test
    fun disconnectAfterDispatchAndRemoteFailureNeverAllowReplay() {
        store.send("Hello")
        val sent = commands.sent.single()
        sent.result.completeExceptionally(
            dev.intent.core.protocol.session.SessionDisconnectedException()
        )
        store.retry(sent.id)
        assertEquals(SendStatus.UNCONFIRMED, store.state.value.attempts.single().status)
        assertEquals(1, commands.sent.size)
        store.send("Next")
        commands.sent.last().result.complete(SendResult(false, false))
        store.retry(commands.sent.last().id)
        assertEquals(SendStatus.UNCONFIRMED, store.state.value.attempts.last().status)
        assertEquals(2, commands.sent.size)
    }

    private fun questionRow() = ConversationMessage(
        "questions",
        "assistant",
        listOf(
            ContentBlock(
                "resource",
                resource = ResourceContents(
                    mimeType = QUESTION_MIME,
                    text = """
                {"attachmentId":"q","header":"Choice","question":"Choose?",
                "options":[{"label":"One"},{"label":"Two"}]}
                    """.trimIndent()
                )
            )
        )
    )
}

private data class SentCommand(
    val id: String,
    val content: String,
    val metadata: JsonObject?,
    val result: CompletableFuture<SendResult> = CompletableFuture()
)

private class FakeCommands : ConversationCommands {
    var images = emptyList<JsonObject>()
    override fun sendAttachments(
        id: String,
        content: String,
        metadata: JsonObject?,
        images: List<JsonObject>,
        files: List<JsonObject>
    ): CompletableFuture<SendResult> {
        this.images = images
        return send(id, content, metadata)
    }
    val sent = mutableListOf<SentCommand>()
    val removals = mutableListOf<CompletableFuture<CommandResult>>()
    var rows = emptyList<ConversationMessage>()
    var queued = emptyList<QueuedMessage>()
    var details = Agent("agent", "Agent")
    var queueReply: CompletableFuture<QueueResult>? = null
    var refresh: () -> Unit = {}
    override fun send(id: String, content: String, metadata: JsonObject?) =
        SentCommand(id, content, metadata).also { sent.add(it) }.result
    override fun queue() =
        queueReply ?: CompletableFuture.completedFuture(QueueResult(true, queued))
    override fun agent() = CompletableFuture.completedFuture(details)
    override fun history(aroundId: String?) =
        CompletableFuture.completedFuture(ConversationPage("agent", rows))
    override fun retract(id: String) = CompletableFuture<CommandResult>().also { removals.add(it) }
    override fun watch(onRefresh: () -> Unit, onError: (Throwable) -> Unit): Closeable {
        refresh = onRefresh
        return Closeable {}
    }
}
