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
    val error: String? = null,
    val retiredAgents: List<Agent> = emptyList(),
    val retiredError: String? = null,
    val retiredLoading: Boolean = false
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
    private var retiredRevision = 0L

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
        refreshRetired()
        val readRevision = ++revision
        mutableState.value = state.value.copy(isLoading = true, error = null)
        session.list().whenComplete { rows, error ->
            synchronized(this) {
                // A newer read, live push, reconnect, or close supersedes this response.
                if (!closed && revision == readRevision) {
                    if (error !=
                        null
                    ) {
                        fail(error)
                    } else {
                        mutableState.publish(rows + state.value.retiredAgents, workspaceId)
                    }
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
            snapshot != null -> mutableState.publish(
                snapshot + state.value.retiredAgents,
                workspaceId
            )
            delta != null -> {
                val reloadRetired = state.value.retiredLoading
                retiredRevision++
                val rows = (state.value.agents + state.value.retiredAgents)
                    .associateBy { it.id }.toMutableMap()
                (delta.added + delta.updated).forEach { rows[it.id] = it }
                delta.removedIds.forEach(rows::remove)
                mutableState.publish(rows.values.toList(), workspaceId)
                if (reloadRetired) refreshRetired()
            }
            else -> fail(IllegalArgumentException("Agent update has no data"))
        }
    }

    private fun refreshRetired() {
        val token = ++retiredRevision
        mutableState.value = state.value.copy(retiredLoading = true, retiredError = null)
        session.retired().whenComplete { rows, error ->
            synchronized(this) {
                if (!closed && token == retiredRevision) {
                    mutableState.value = state.value.copy(
                        retiredLoading = false,
                        retiredError = if (error != null) "Cannot load retired agents" else null,
                        retiredAgents = if (error != null) {
                            state.value.retiredAgents
                        } else {
                            scopedAgents(rows, workspaceId).filter { it.isRetired() }
                        }
                    )
                }
            }
        }
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
        retiredRevision++
        mutableState.value = state.value.copy(retiredLoading = false)
        mutableState.publish(
            (state.value.agents + state.value.retiredAgents).map {
                if (it.id == agent.id) agent else it
            },
            workspaceId
        )
    }

    @Synchronized
    fun removed(agentId: String) {
        if (closed) return
        revision++
        retiredRevision++
        mutableState.value = state.value.copy(retiredLoading = false)
        mutableState.publish(
            (state.value.agents + state.value.retiredAgents).filterNot { it.id == agentId },
            workspaceId
        )
    }

    @Synchronized
    fun stop() {
        subscriptionGeneration++
        retiredRevision++
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

private fun scopedAgents(rows: List<Agent>, workspaceId: String) = rows.distinctBy {
    it.id
}.filter {
    (it.workspaceId == null || it.workspaceId == workspaceId) && it.status != "deleted"
}.sortedWith(compareByDescending<Agent> { it.lastActivity ?: it.updatedAt }.thenBy { it.id })

private fun MutableStateFlow<AgentListState>.publish(rows: List<Agent>, workspaceId: String) {
    val scoped = scopedAgents(rows, workspaceId)
    value = value.copy(
        agents = scoped.filterNot { it.isRetired() },
        retiredAgents = scoped.filter { it.isRetired() },
        isLoading = false,
        error = null
    )
}
