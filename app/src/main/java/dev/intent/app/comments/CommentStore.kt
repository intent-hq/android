package dev.intent.app.comments

import dev.intent.core.protocol.models.CommentThreadSummary
import dev.intent.core.protocol.models.SubscriptionPush
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommentState(
    val threads: List<CommentThreadSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val drafts: Map<String, String> = emptyMap(),
    val sending: Set<String> = emptySet(),
    val replyErrors: Map<String, String> = emptyMap()
)

/** Full thread rows include comments. RpcSession owns sequence checks and reconnect subscriptions. */
class CommentStore(private val session: CommentSession, private val noteId: String) : Closeable {
    private val mutableState = MutableStateFlow(CommentState())
    val state = mutableState.asStateFlow()
    private var lease: Closeable? = null
    private var closed = false
    private var revision = 0L

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        lease = session.subscribe(::applyPush, ::refresh) {
            synchronized(this) {
                if (!closed) {
                    revision++
                    mutableState.value =
                        state.value.copy(
                            loading = false,
                            error = "Cannot load comments. Refresh to try again."
                        )
                }
            }
        }
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val read = ++revision
        mutableState.value = state.value.copy(loading = true, error = null)
        session.list().whenComplete { rows, error ->
            synchronized(this) {
                if (!closed && read == revision) {
                    if (error == null) {
                        publish(rows)
                    } else {
                        mutableState.value =
                            state.value.copy(
                                loading = false,
                                error = "Cannot load comments. Refresh to try again."
                            )
                    }
                }
            }
        }
    }

    @Synchronized
    private fun applyPush(push: SubscriptionPush<CommentThreadSummary>) {
        if (closed) return
        revision++
        val snapshot = push.snapshot
        val delta = push.delta
        when {
            snapshot != null -> publish(snapshot)
            delta != null -> {
                val rows = state.value.threads.associateBy { it.threadId }.toMutableMap()
                (delta.added + delta.updated).forEach { rows[it.threadId] = it }
                delta.removedIds.forEach(rows::remove)
                publish(rows.values.toList())
            }
            else -> refresh()
        }
    }

    private fun publish(rows: List<CommentThreadSummary>) {
        mutableState.value = state.value.copy(
            threads = rows.filter { it.noteId == noteId }.distinctBy { it.threadId }
                .sortedWith(
                    compareByDescending<CommentThreadSummary> {
                        it.lastActivity
                    }.thenBy { it.threadId }
                ),
            loading = false,
            error = null
        )
    }

    @Synchronized
    fun draft(threadId: String, text: String) {
        if (!closed) {
            mutableState.value =
                state.value.copy(drafts = state.value.drafts + (threadId to text))
        }
    }

    @Synchronized
    fun reply(threadId: String) {
        val text = state.value.drafts[threadId].orEmpty()
        if (closed || text.isBlank()) return
        if (threadId in state.value.sending ||
            state.value.threads.none { it.threadId == threadId }
        ) {
            return
        }
        mutableState.value = state.value.copy(
            sending = state.value.sending + threadId,
            replyErrors = state.value.replyErrors - threadId
        )
        session.reply(threadId, text).whenComplete { result, error ->
            synchronized(this) {
                if (!closed) {
                    val success =
                        error == null && result.success && result.thread.threadId == threadId
                    mutableState.value = state.value.copy(
                        sending = state.value.sending - threadId,
                        drafts = if (success &&
                            state.value.drafts[threadId] == text
                        ) {
                            state.value.drafts - threadId
                        } else {
                            state.value.drafts
                        },
                        replyErrors = if (success) {
                            state.value.replyErrors - threadId
                        } else {
                            state.value.replyErrors +
                                (
                                    threadId to
                                        "Reply was not confirmed. Refresh and check the thread before sending again."
                                    )
                        }
                    )
                    // Never replay a mutation after reconnect or an uncertain response.
                    refresh()
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        revision++
        lease?.close()
        lease = null
    }
}
