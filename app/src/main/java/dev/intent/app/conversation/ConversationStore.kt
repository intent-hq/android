package dev.intent.app.conversation

import dev.intent.core.protocol.models.ChatEntity
import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.EntityDelta
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConversationState(
    val messages: List<ConversationMessage> = emptyList(),
    val nextToken: String? = null,
    val isLoading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hydrating: Set<String> = emptySet(),
    val error: String? = null,
    val isStreaming: Boolean = false
)

/** Snapshot epochs fence paging and hydration replies across reconnects and invalidations. */
@Suppress("TooManyFunctions") // Paging and refresh share the same synchronized lifecycle.
class ConversationStore(private val session: ConversationSession, private val agentId: String) :
    Closeable {
    private val mutableState = MutableStateFlow(ConversationState())
    val state = mutableState.asStateFlow()
    private var lease: Closeable? = null
    private var activityLease: Closeable? = null
    private var chatLive = false
    private var generation = 0L
    private var epoch = 0L
    private var closed = false
    private var refreshHistory: ConversationState? = null
    private var refreshGap: RefreshGap? = null

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        val current = ++generation
        var subscribed = false
        lease = session.subscribe(
            { push -> guarded(current) { apply(push) } },
            {
                guarded(current) {
                    // Only the requested refresh retains history, not a later reconnect.
                    if (subscribed) refreshHistory = null
                    subscribed = true
                    resetPending()
                }
            },
            { guarded(current) { fail() } }
        )
        activityLease = session.subscribeActivity { activity ->
            guarded(current) {
                val streaming = activity.streaming(chatLive)
                if (streaming != null) {
                    mutableState.value = state.value.copy(isStreaming = streaming)
                }
            }
        }
    }

    @Synchronized
    private fun guarded(current: Long, action: () -> Unit) {
        if (!closed && generation == current) action()
    }

    private fun resetPending() {
        refreshGap = null
        chatLive = false
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
            chatLive = true
            epoch++
            val retained = refreshHistory
            refreshHistory = null
            refreshGap = null
            val tail = ConversationState(
                snapshot.messages.distinctBy { it.id },
                snapshot.nextToken,
                false,
                isStreaming = snapshot.turnInFlight ||
                    snapshot.isResponding ||
                    snapshot.messages.any { it.isStreaming == true }
            )
            if (retained == null || retained.messages.isEmpty() || tail.messages.isEmpty()) {
                mutableState.value = tail
            } else {
                refreshGap = RefreshGap(retained, tail)
                mutableState.value =
                    state.value.copy(
                        isLoading = false,
                        loadingOlder = true,
                        isStreaming = tail.isStreaming
                    )
                fillRefreshGap()
            }
        } else if (delta != null && !state.value.isLoading) {
            refreshGap?.deltas?.add(delta)
            mutableState.value = state.value.copy(
                messages = reduceConversation(state.value.messages, delta, agentId),
                isStreaming = delta.streaming(state.value.isStreaming, agentId),
                error = null
            )
        }
    }

    private fun fillRefreshGap() {
        val gap = refreshGap ?: return
        val tail = gap.tail
        val retainedIds = gap.retained.messages.map { it.id }.toSet()
        val overlap = tail.messages.indexOfFirst { it.id in retainedIds }
        val token = tail.nextToken
        val pagingStopped = gap.tokens.size >= MAX_REFRESH_PAGES || token in gap.tokens
        if (overlap >= 0 || token == null || pagingStopped) {
            val retainedIndex = gap.retained.messages.indexOfFirst {
                it.id ==
                    tail.messages.firstOrNull()?.id
            }
            val prefix = gap.retained.messages.take(retainedIndex.coerceAtLeast(0))
            mutableState.value = tail.copy(
                messages = gap.deltas.fold(prefix + tail.messages) { messages, delta ->
                    reduceConversation(messages, delta, agentId)
                },
                nextToken = if (retainedIndex >= 0) gap.retained.nextToken else token,
                isStreaming = state.value.isStreaming
            )
            refreshGap = null
            return
        }
        gap.tokens.add(token)
        val current = epoch
        session.history(token).whenComplete { page, error ->
            synchronized(this) {
                if (!closed && epoch == current && refreshGap === gap) {
                    if (error != null || page.agentId != agentId) {
                        fail()
                    } else {
                        gap.prepend(page)
                        fillRefreshGap()
                    }
                }
            }
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
        refreshHistory = null
        refreshGap = null
        mutableState.value =
            state.value.copy(
                isLoading = false,
                loadingOlder = false,
                error = "Cannot load conversation. Retry."
            )
    }

    @Synchronized
    fun refresh() {
        val history = state.value
        stop()
        refreshHistory = history
        start()
    }

    @Synchronized
    fun stop() {
        refreshHistory = null
        generation++
        resetPending()
        lease?.close()
        lease = null
        activityLease?.close()
        activityLease = null
    }

    @Synchronized
    override fun close() {
        closed = true
        stop()
    }
}

private const val MAX_REFRESH_PAGES = 10

private class RefreshGap(val retained: ConversationState, var tail: ConversationState) {
    val tokens = mutableSetOf<String>()
    val deltas = mutableListOf<EntityDelta<ChatEntity>>()

    fun prepend(page: ConversationPage) {
        // Snapshot rows and live updates win over older page copies.
        val currentIds = tail.messages.map { it.id }.toSet()
        tail = tail.copy(
            messages =
            page.messages.distinctBy { it.id }.filter { it.id !in currentIds } + tail.messages,
            nextToken = page.nextToken
        )
    }
}
