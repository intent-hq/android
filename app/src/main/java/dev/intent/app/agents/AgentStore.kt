package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.SubscriptionPush
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentListState(
    val agents: List<Agent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val tree: AgentTree = AgentTree(agents)
}

/** One store belongs to one daemon session. Closing it does not close that session. */
class AgentStore(private val session: AgentSession, private val workspaceId: String) : Closeable {
    private val mutableState = MutableStateFlow(AgentListState())
    val state: StateFlow<AgentListState> = mutableState.asStateFlow()
    private var lease: Closeable? = null
    private var closed = false
    private var revision = 0L
    private var subscriptionGeneration = 0L

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        val generation = ++subscriptionGeneration
        lease = session.subscribe(
            { push -> ifCurrent(generation) { applyPush(push) } },
            { ifCurrent(generation) { refresh() } },
            { error -> ifCurrent(generation) { fail(error) } }
        )
    }

    @Synchronized
    private fun ifCurrent(generation: Long, action: () -> Unit) {
        if (!closed && generation == subscriptionGeneration) action()
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val readRevision = ++revision
        mutableState.value = state.value.copy(isLoading = true, error = null)
        session.list().whenComplete { rows, error ->
            synchronized(this) {
                // A newer read, live push, reconnect, or close supersedes this response.
                if (!closed && revision == readRevision) {
                    if (error != null) fail(error) else publish(rows)
                }
            }
        }
    }

    @Synchronized
    private fun applyPush(push: SubscriptionPush<Agent>) {
        if (closed) return
        revision++
        val snapshot = push.snapshot
        val delta = push.delta
        when {
            snapshot != null -> publish(snapshot)
            delta != null -> {
                val rows = state.value.agents.associateBy { it.id }.toMutableMap()
                (delta.added + delta.updated).forEach { rows[it.id] = it }
                delta.removedIds.forEach(rows::remove)
                publish(rows.values.toList())
            }
            else -> fail(IllegalArgumentException("Agent update has no data"))
        }
    }

    private fun publish(rows: List<Agent>) {
        mutableState.value = AgentListState(
            agents = rows.distinctBy { it.id }.filter {
                (it.workspaceId == null || it.workspaceId == workspaceId) &&
                    it.retiredAt == null &&
                    it.status.lowercase() !in setOf("deleted", "retired")
            }
                .sortedWith(
                    compareByDescending<Agent> {
                        it.lastActivity ?: it.updatedAt
                    }.thenBy { it.id }
                ),
            isLoading = false,
            error = null
        )
    }

    @Synchronized
    private fun fail(error: Throwable) {
        if (!closed) {
            revision++
            mutableState.value =
                state.value.copy(
                    isLoading = false,
                    error =
                    error.message ?: "Cannot load agents"
                )
        }
    }

    @Synchronized
    fun changed(agent: Agent) {
        if (closed) return
        revision++
        publish(state.value.agents.map { if (it.id == agent.id) agent else it })
    }

    @Synchronized
    fun removed(agentId: String) {
        if (closed) return
        revision++
        publish(state.value.agents.filterNot { it.id == agentId })
    }

    @Synchronized
    fun stop() {
        subscriptionGeneration++
        revision++
        lease?.close()
        lease = null
    }

    @Synchronized
    override fun close() {
        closed = true
        stop()
    }
}
