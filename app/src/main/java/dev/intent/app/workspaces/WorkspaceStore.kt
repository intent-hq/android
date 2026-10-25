package dev.intent.app.workspaces

import dev.intent.app.cache.OfflineCache
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkspaceListState(
    val workspaces: List<Workspace> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** One store belongs to one daemon session. Closing it does not close that session. */
class WorkspaceStore(
    private val session: WorkspaceSession,
    private val cache: OfflineCache? = null
) : Closeable {
    private val mutableState = MutableStateFlow(WorkspaceListState())
    val state: StateFlow<WorkspaceListState> = mutableState.asStateFlow()
    private var lease: Closeable? = null
    private var closed = false
    private var dataVersion = 0L
    private var revision = 0L
    private var subscriptionGeneration = 0L

    @Synchronized
    fun start() {
        if (closed || lease != null) return
        val generation = ++subscriptionGeneration
        val cachedVersion = dataVersion
        cache?.workspaces()?.whenComplete { rows, error ->
            ifCurrent(generation) {
                if (cachedVersion == dataVersion) {
                    if (error == null) {
                        publish(rows, fromCache = true)
                    } else {
                        mutableState.value =
                            state.value.copy(isLoading = false, error = "Cannot read offline cache")
                    }
                }
            }
        }
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
    private fun applyPush(push: SubscriptionPush<Workspace>) {
        if (closed) return
        revision++
        val snapshot = push.snapshot
        val delta = push.delta
        when {
            snapshot != null -> publish(snapshot)
            delta != null -> {
                val rows = state.value.workspaces.associateBy { it.id }.toMutableMap()
                (delta.added + delta.updated).forEach { rows[it.id] = it }
                delta.removedIds.forEach(rows::remove)
                publish(rows.values.toList())
            }
            else -> fail(IllegalArgumentException("Workspace update has no data"))
        }
    }

    private fun publish(rows: List<Workspace>, fromCache: Boolean = false) {
        if (!fromCache) dataVersion++
        mutableState.value = WorkspaceListState(
            workspaces = rows.distinctBy { it.id }.filter {
                it.status != "Deleted" &&
                    it.status != "Retired"
            }
                .sortedWith(
                    compareByDescending<Workspace> {
                        it.lastActivity ?: it.updatedAt
                    }.thenBy { it.id }
                ),
            isLoading = false,
            error = if (fromCache) state.value.error else null
        )
        if (!fromCache) cache?.replaceWorkspaces(state.value.workspaces)
    }

    @Synchronized
    private fun fail(error: Throwable) {
        if (!closed) {
            revision++
            mutableState.value =
                state.value.copy(
                    isLoading = false,
                    error =
                    error.message ?: "Cannot load workspaces"
                )
        }
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
