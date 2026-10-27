package dev.intent.app.conversation

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.QueuedMessage
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class SendStatus { SENDING, UNCONFIRMED, ACCEPTED, QUEUED }

data class SendAttempt(
    val id: String,
    val content: String,
    val metadata: JsonObject? = null,
    val status: SendStatus = SendStatus.SENDING,
    val confirmedId: String? = null,
    val images: List<JsonObject> = emptyList(),
    val files: List<JsonObject> = emptyList()
)

data class ConversationSendState(
    val attempts: List<SendAttempt> = emptyList(),
    val queue: List<QueuedMessage> = emptyList(),
    val questions: QuestionSet? = null,
    val checking: Boolean = false,
    val retracting: Set<String> = emptySet(),
    val error: String? = null
)

/** Failed mutations stay uncertain. Reads can prove acceptance, never non-delivery. */
@Suppress("TooManyFunctions")
class ConversationSendStore(
    private val commands: ConversationCommands,
    private val agentId: String
) : Closeable {
    private val mutableState = MutableStateFlow(ConversationSendState())
    val state = mutableState.asStateFlow()
    private var lease: Closeable? = null
    private var generation = 0L
    private var readVersion = 0L
    private var closed = false
    private var agent: Agent? = null
    private var messages = emptyList<ConversationMessage>()

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        val current = ++generation
        lease = commands.watch(
            { synchronized(this) { if (current == generation && !closed) refresh() } },
            { synchronized(this) { if (current == generation && !closed) fail() } }
        )
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val current = ++readVersion
        mutableState.value = state.value.copy(checking = true, error = null)
        commands.queue().thenCombine(commands.agent()) { queue, details ->
            check(queue.success && details.id == agentId)
            queue to details
        }.thenCombine(commands.history()) { (queue, details), page ->
            check(page.agentId == agentId)
            Triple(queue.queue, details, page.messages)
        }.whenComplete { result, error ->
            synchronized(this) {
                if (!closed && current == readVersion) {
                    if (error != null) {
                        fail()
                    } else {
                        val (queue, details, history) = result
                        agent = details
                        messages = history
                        mutableState.value = state.value.copy(queue = queue, checking = false)
                        reconcile()
                        val marker = details.metadata.string("pendingQuestionsMessageId")
                        if (!marker.isNullOrEmpty() && messages.none { it.id == marker }) {
                            readAround(marker, current)
                        }
                        state.value.attempts.filter {
                            it.status == SendStatus.UNCONFIRMED ||
                                it.status == SendStatus.ACCEPTED
                        }
                            .forEach { readAround(it.confirmedId ?: it.id, current) }
                    }
                }
            }
        }
    }

    private fun readAround(id: String, current: Long) {
        commands.history(id).whenComplete { page, error ->
            synchronized(this) {
                val valid = !closed && current == readVersion
                if (valid && error == null && page.agentId == agentId) {
                    messages = (messages + page.messages).distinctBy { it.id }
                    reconcile()
                }
            }
        }
    }

    @Synchronized
    fun observe(incoming: List<ConversationMessage>) {
        if (closed) return
        messages = (incoming + messages).distinctBy { it.id }
        reconcile()
    }

    private fun reconcile() {
        val attempts = state.value.attempts.mapNotNull { attempt ->
            val row = messages.firstOrNull { row ->
                row.role == "user" &&
                    matches(
                        attempt,
                        row.id,
                        row.appMessageId ?: row.metadata.string("userAppMessageId")
                    )
            }
            if (row != null) {
                null
            } else {
                val queued = state.value.queue.firstOrNull {
                    matches(attempt, it.id, it.messageMetadata.string("userAppMessageId"))
                }
                if (queued == null || attempt.status == SendStatus.ACCEPTED) {
                    attempt
                } else {
                    attempt.copy(status = SendStatus.QUEUED, confirmedId = queued.id)
                }
            }
        }
        mutableState.value = state.value.copy(attempts = attempts)
        updateQuestions()
    }

    private fun matches(attempt: SendAttempt, rowId: String, appId: String?): Boolean = if (appId !=
        null
    ) {
        appId == attempt.id
    } else {
        rowId == attempt.id || rowId == attempt.confirmedId
    }

    private fun updateQuestions() {
        val details = agent ?: return
        val marker = details.metadata.string("pendingQuestionsMessageId")
        val row = if (marker != null) {
            messages.find { it.id == marker }
        } else {
            messages.lastOrNull { it.role != "system" }?.takeUnless { details.turnInFlight }
        }
        val answered = messages.any { it.role == "user" && it.answeredQuestion() == row?.id } ||
            state.value.queue.any { it.messageMetadata.answeredQuestion() == row?.id } ||
            state.value.attempts.any { it.metadata.answeredQuestion() == row?.id }
        val questions = row?.contentBlocks.orEmpty().mapNotNull {
            it.question()
        }.distinctBy { it.attachmentId }
        val eligible = row != null && row.role == "assistant" && row.isStreaming != true
        val retired = answered || row?.id == details.metadata.string("dismissedQuestionsMessageId")
        val set = if (!eligible || retired || questions.isEmpty()
        ) {
            null
        } else {
            QuestionSet(requireNotNull(row).id, questions)
        }
        mutableState.value = state.value.copy(questions = set)
    }

    @Synchronized
    fun send(
        content: String,
        metadata: JsonObject? = null,
        images: List<JsonObject> = emptyList(),
        files: List<JsonObject> = emptyList()
    ): Boolean {
        val empty = content.isBlank() && images.isEmpty() && files.isEmpty()
        if (closed || empty) return false
        val attempt =
            SendAttempt(
                UUID.randomUUID().toString(),
                content.trim(),
                metadata,
                images = images.toList(),
                files = files.toList()
            )
        mutableState.value =
            state.value.copy(attempts = state.value.attempts + attempt, error = null)
        updateQuestions()
        commands.sendAttachments(
            attempt.id,
            attempt.content,
            metadata,
            attempt.images,
            attempt.files
        ).whenComplete {
                result,
                error
            ->
            synchronized(this) {
                if (!closed) {
                    mutableState.value =
                        state.value.copy(
                            attempts = state.value.attempts.map { existing ->
                                if (existing.id != attempt.id ||
                                    existing.status != SendStatus.SENDING
                                ) {
                                    existing
                                } else if (error != null ||
                                    result?.success != true
                                ) {
                                    existing.copy(status = SendStatus.UNCONFIRMED)
                                } else {
                                    existing.copy(
                                        status = if (result.queued) {
                                            SendStatus.QUEUED
                                        } else {
                                            SendStatus.ACCEPTED
                                        },
                                        confirmedId = result.queuedMessage?.id ?: result.messageId
                                    )
                                }
                            }
                        )
                    refresh()
                }
            }
        }
        return true
    }

    /** Suggestions never read or consume composer text or attachments. */
    @Synchronized
    fun sendSuggestedPrompt(prompt: String): Boolean {
        if (state.value.attempts.isNotEmpty()) return false
        return send(prompt)
    }

    @Synchronized
    fun answer(messageId: String, answers: Map<String, QuestionAnswer>): Boolean {
        val set = state.value.questions?.takeIf { it.messageId == messageId } ?: return false
        return send(
            flattenAnswers(set, answers),
            buildJsonObject {
                put("type", "question_answers")
                put("answeredQuestionsMessageId", messageId)
            }
        )
    }

    @Synchronized
    fun retract(id: String) {
        if (closed || id in state.value.retracting || state.value.queue.none { it.id == id }) return
        ++readVersion
        mutableState.value =
            state.value.copy(retracting = state.value.retracting + id, error = null)
        commands.retract(id).whenComplete { result, error ->
            synchronized(this) {
                if (!closed) {
                    mutableState.value = state.value.copy(retracting = state.value.retracting - id)
                    if (error == null && result?.success == true) {
                        mutableState.value = state.value.copy(
                            queue = state.value.queue.filterNot { it.id == id },
                            attempts = state.value.attempts.filterNot {
                                it.confirmedId == id &&
                                    it.status == SendStatus.QUEUED
                            }
                        )
                        updateQuestions()
                        refresh()
                    } else {
                        fail("Cannot confirm removal. Check status before trying again.")
                    }
                }
            }
        }
    }

    private fun fail(message: String = "Cannot check message status. Refresh to try again.") {
        mutableState.value = state.value.copy(checking = false, error = message)
    }

    @Synchronized
    fun stop() {
        generation++
        readVersion++
        lease?.close()
        lease = null
    }

    @Synchronized
    override fun close() {
        closed = true
        stop()
    }
}
