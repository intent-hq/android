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

enum class SendStatus { SENDING, NOT_SENT, UNCONFIRMED, ACCEPTED, QUEUED }

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
    val running: Boolean = false,
    val stopping: Boolean = false,
    val dismissing: String? = null,
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
    private val dismissedQuestions = mutableSetOf<String>()
    private var stopRequested = false
    private var stopPending = false
    private val queueAwaitingRefresh = mutableSetOf<String>()
    private var messages = emptyList<ConversationMessage>()
    private val confirmations = mutableMapOf<String, () -> Unit>()

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
                        observeRunning(details.conversationRunning())
                        messages = history
                        mutableState.value = state.value.copy(
                            queue = queue,
                            checking = false,
                            retracting = state.value.retracting - queueAwaitingRefresh
                        )
                        queueAwaitingRefresh.clear()
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
                confirmations.remove(attempt.id)?.invoke()
                null
            } else {
                val queued = state.value.queue.firstOrNull {
                    matches(attempt, it.id, it.messageMetadata.string("userAppMessageId"))
                }
                if (queued == null || attempt.status == SendStatus.ACCEPTED) {
                    attempt
                } else {
                    confirmations.remove(attempt.id)?.invoke()
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
        val retired =
            row?.id in dismissedQuestions ||
                answered ||
                row?.id == details.metadata.string("dismissedQuestionsMessageId")
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
        files: List<JsonObject> = emptyList(),
        onConfirmed: () -> Unit = {}
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
        confirmations[attempt.id] = onConfirmed
        updateQuestions()
        dispatch(attempt)
        return true
    }

    /** Retry only a request that the local transport proves was never dispatched. */
    @Synchronized
    fun retry(id: String) {
        if (closed) return
        val attempt = state.value.attempts.find { it.id == id && it.status == SendStatus.NOT_SENT }
            ?.copy(status = SendStatus.SENDING) ?: return
        mutableState.value = state.value.copy(
            attempts = state.value.attempts.map { if (it.id == id) attempt else it },
            error = null
        )
        dispatch(attempt)
    }

    private fun dispatch(attempt: SendAttempt) {
        commands.sendAttachments(
            attempt.id,
            attempt.content,
            attempt.metadata,
            attempt.images,
            attempt.files
        ).whenComplete {
                result,
                error
            ->
            synchronized(this) {
                if (!closed) {
                    if (error == null && result?.success == true) {
                        confirmations.remove(attempt.id)?.invoke()
                    }
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
                                    existing.copy(status = sendFailureStatus(error))
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
    }

    /** Suggestions never read or consume composer text or attachments. */
    @Synchronized
    fun sendSuggestedPrompt(prompt: String): Boolean {
        if (state.value.attempts.isNotEmpty()) return false
        return send(prompt)
    }

    @Synchronized
    fun answer(messageId: String, answers: Map<String, QuestionAnswer>): Boolean {
        val set = state.value.questions?.takeIf {
            it.messageId == messageId && state.value.dismissing == null
        } ?: return false
        return send(
            flattenAnswers(set, answers),
            buildJsonObject {
                put("type", "question_answers")
                put("answeredQuestionsMessageId", messageId)
            }
        )
    }

    @Synchronized
    fun observeRunning(running: Boolean) {
        if (closed) return
        if (!running) stopRequested = false
        mutableState.value = state.value.copy(
            running = running,
            stopping = stopPending || stopRequested
        )
    }

    @Synchronized
    fun stopReply() {
        if (closed || !state.value.running || state.value.stopping) return
        stopPending = true
        stopRequested = true
        mutableState.value = state.value.copy(stopping = true, error = null)
        commands.stopReply().whenComplete { result, error ->
            synchronized(this) {
                if (!closed) {
                    stopPending = false
                    if (error != null || result?.success != true) {
                        stopRequested = false
                    }
                    mutableState.value = state.value.copy(stopping = stopRequested)
                    refresh()
                    if (error != null || result?.success != true) {
                        fail("Cannot confirm stop. Refresh before trying again.")
                    }
                }
            }
        }
    }

    @Synchronized
    fun editQueued(id: String, content: String) {
        if (content.isBlank()) return
        queueMutation(id) {
            commands.editQueued(id, content.trim()).thenApply {
                check(it.success)
                state.value.queue.map { row ->
                    if (row.id ==
                        id
                    ) {
                        row.copy(content = content.trim())
                    } else {
                        row
                    }
                }
            }
        }
    }

    @Synchronized
    fun sendNow(id: String) {
        queueMutation(id) {
            commands.sendNow(id).thenApply {
                check(it.success)
                if (it.queued) {
                    state.value.queue
                } else {
                    state.value.queue.filterNot { row ->
                        row.id ==
                            id
                    }
                }
            }
        }
    }

    @Synchronized
    fun retract(id: String) {
        queueMutation(id) {
            commands.retract(id).thenApply {
                check(it.success)
                state.value.queue.filterNot { row -> row.id == id }
            }
        }
    }

    private fun queueMutation(
        id: String,
        request: () -> java.util.concurrent.CompletableFuture<List<QueuedMessage>>
    ) {
        if (closed || id in state.value.retracting || state.value.queue.none { it.id == id }) return
        ++readVersion
        mutableState.value =
            state.value.copy(retracting = state.value.retracting + id, error = null)
        request().whenComplete { queue, error ->
            synchronized(this) {
                if (!closed) {
                    ++readVersion
                    queueAwaitingRefresh.add(id)
                    if (error == null) {
                        mutableState.value = state.value.copy(
                            queue = queue,
                            attempts = state.value.attempts.filterNot {
                                it.status == SendStatus.QUEUED &&
                                    it.confirmedId == id &&
                                    queue.none { row -> row.id == id }
                            }
                        )
                        updateQuestions()
                    }
                    refresh()
                    if (error != null) {
                        fail("Cannot confirm queue change. Refresh before trying again.")
                    }
                }
            }
        }
    }

    @Synchronized
    fun dismissQuestions(id: String) {
        if (closed ||
            state.value.questions?.messageId != id ||
            state.value.dismissing != null
        ) {
            return
        }
        ++readVersion
        mutableState.value = state.value.copy(dismissing = id, error = null)
        commands.dismissQuestions(id).whenComplete { result, error ->
            synchronized(this) {
                if (!closed) {
                    ++readVersion
                    mutableState.value = state.value.copy(dismissing = null)
                    if (error == null && result?.success == true) {
                        dismissedQuestions.add(id)
                        updateQuestions()
                    }
                    refresh()
                    if (error != null || result?.success != true) {
                        fail("Cannot confirm dismissal. Refresh before trying again.")
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
        confirmations.clear()
        stop()
    }
}

private fun sendFailureStatus(error: Throwable?): SendStatus {
    var cause = error
    while (cause is java.util.concurrent.CompletionException) cause = cause.cause
    return if (cause is dev.intent.core.protocol.session.SessionDisconnectedException &&
        cause.requestNotSent
    ) {
        SendStatus.NOT_SENT
    } else {
        SendStatus.UNCONFIRMED
    }
}
