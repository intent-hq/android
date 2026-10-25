package dev.intent.app.conversation

import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ConversationMessage
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConversationState(
    val messages: List<ConversationMessage> = emptyList(),
    val nextToken: String? = null,
    val isLoading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hydrating: Set<String> = emptySet(),
    val error: String? = null
)

/** Snapshot epochs fence paging and hydration replies across reconnects and invalidations. */
class ConversationStore(private val session: ConversationSession, private val agentId: String) :
    Closeable {
    private val mutableState = MutableStateFlow(ConversationState())
    val state = mutableState.asStateFlow()
    private var lease: Closeable? = null
    private var generation = 0L
    private var epoch = 0L
    private var closed = false

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        val current = ++generation
        lease = session.subscribe(
            { push -> guarded(current) { apply(push) } },
            { guarded(current) { resetPending() } },
            { guarded(current) { fail() } }
        )
    }

    @Synchronized
    private fun guarded(current: Long, action: () -> Unit) {
        if (!closed && generation == current) action()
    }

    private fun resetPending() {
        epoch++
        mutableState.value =
            state.value.copy(
                isLoading = true,
                loadingOlder = false,
                hydrating = emptySet(),
                error = null
            )
    }

    private fun apply(push: ChatPush) {
        val snapshot = push.snapshot
        val delta = push.delta
        if (snapshot != null) {
            if (snapshot.agentId != agentId) return
            epoch++
            mutableState.value =
                ConversationState(snapshot.messages.distinctBy { it.id }, snapshot.nextToken, false)
        } else if (delta != null && !state.value.isLoading) {
            mutableState.value = state.value.copy(
                messages = reduceConversation(state.value.messages, delta, agentId),
                error = null
            )
        }
    }

    @Synchronized
    fun loadOlder() {
        val token = state.value.nextToken ?: return
        if (closed || state.value.isLoading || state.value.loadingOlder) return
        val current = epoch
        mutableState.value = state.value.copy(loadingOlder = true, error = null)
        session.history(token).whenComplete { page, error ->
            synchronized(this) {
                if (!closed && epoch == current) {
                    if (error != null || page.agentId != agentId) {
                        fail()
                    } else {
                        // Current rows win overlap: live updates cannot be erased by a slow page.
                        val currentIds = state.value.messages.map { it.id }.toSet()
                        mutableState.value = state.value.copy(
                            messages =
                            page.messages.filter {
                                it.id !in currentIds
                            } + state.value.messages,
                            nextToken = page.nextToken?.takeUnless { it == token },
                            loadingOlder = false
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    fun hydrate(messageId: String, blockId: String) {
        val original =
            state.value.messages.find {
                it.id == messageId
            }?.contentBlocks?.find { it.id == blockId }
                ?: return
        if (closed || blockId in state.value.hydrating) return
        val current = epoch
        mutableState.value =
            state.value.copy(hydrating = state.value.hydrating + blockId, error = null)
        session.hydrate(messageId, blockId).whenComplete { result, error ->
            synchronized(this) {
                if (!closed && epoch == current) {
                    mutableState.value =
                        state.value.copy(hydrating = state.value.hydrating - blockId)
                    if (error != null || result.block.id != blockId) {
                        fail()
                    } else {
                        mutableState.value =
                            state.value.copy(
                                messages = state.value.messages.map { message ->
                                    if (message.id !=
                                        messageId
                                    ) {
                                        message
                                    } else {
                                        message.copy(
                                            contentBlocks = message.contentBlocks.map {
                                                // A live block replacement always wins over an older read.
                                                if (it == original) result.block else it
                                            }
                                        )
                                    }
                                }
                            )
                    }
                }
            }
        }
    }

    private fun fail() {
        mutableState.value =
            state.value.copy(
                isLoading = false,
                loadingOlder = false,
                error = "Cannot load conversation. Retry."
            )
    }

    @Synchronized
    fun refresh() {
        stop()
        start()
    }

    @Synchronized
    fun stop() {
        generation++
        resetPending()
        lease?.close()
        lease = null
    }

    @Synchronized
    override fun close() {
        closed = true
        stop()
    }
}
