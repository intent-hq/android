package dev.intentapp.android.workspaces

import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.Workspace
import dev.intentapp.android.cache.OfflineCache
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
    private var pushesDuringRead: MutableList<SubscriptionPush<Workspace>>? = null

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

    /** A pull also wakes a silent or waiting connection, like returning to the foreground. */
    fun pull() {
        session.recover()
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val readRevision = ++revision
        val pushes = mutableListOf<SubscriptionPush<Workspace>>()
        pushesDuringRead = pushes
        mutableState.value = state.value.copy(isLoading = true, error = null)
        session.list().whenComplete { rows, error ->
            synchronized(this) {
                // A newer read, reconnect, mutation, or close supersedes this response. Live pushes
                // do not: they replay on top of it so busy workspaces cannot discard every pull.
                if (!closed && revision == readRevision) {
                    pushesDuringRead = null
                    if (error != null) {
                        fail(error)
                    } else {
                        var merged: List<Workspace>? = rows
                        pushes.forEach { push -> merged = merged?.let { merge(it, push) } }
                        merged?.let(::publish) ?: fail(MalformedPush())
                    }
                }
            }
        }
    }

    @Synchronized
    private fun applyPush(push: SubscriptionPush<Workspace>) {
        if (closed) return
        pushesDuringRead?.add(push)
        val rows = merge(state.value.workspaces, push) ?: return fail(MalformedPush())
        publish(rows, keepLoading = pushesDuringRead != null)
        // A paged snapshot holds only the oldest rows; read every page for the full list.
        if (push.snapshot != null && push.nextToken != null && pushesDuringRead == null) refresh()
    }

    private fun publish(
        rows: List<Workspace>,
        fromCache: Boolean = false,
        keepLoading: Boolean = false
    ) {
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
            isLoading = keepLoading,
            error = if (fromCache) state.value.error else null
        )
        if (!fromCache) cache?.replaceWorkspaces(state.value.workspaces)
    }

    @Synchronized
    private fun fail(error: Throwable) {
        if (!closed) {
            revision++
            pushesDuringRead = null
            mutableState.value =
                state.value.copy(
                    isLoading = false,
                    error =
                    error.message ?: "Cannot load workspaces"
                )
        }
    }

    /** Fence older list reads before applying a server-confirmed mutation. */
    @Synchronized
    internal fun confirmChange(id: String, title: String? = null, archived: Boolean = false) {
        if (closed) return
        revision++
        pushesDuringRead = null
        val rows = state.value.workspaces.mapNotNull {
            when {
                it.id != id -> it
                title != null -> it.copy(title = title)
                archived -> it.copy(status = "Archived")
                else -> null
            }
        }
        publish(rows)
    }

    @Synchronized
    fun stop() {
        subscriptionGeneration++
        revision++
        pushesDuringRead = null
        lease?.close()
        lease = null
    }

    @Synchronized
    override fun close() {
        closed = true
        stop()
    }
}

private fun merge(rows: List<Workspace>, push: SubscriptionPush<Workspace>): List<Workspace>? {
    val delta = push.delta
    val snapshot = push.snapshot
    return when {
        snapshot != null && push.nextToken != null ->
            (rows.associateBy { it.id } + snapshot.associateBy { it.id }).values.toList()
        snapshot != null -> snapshot
        delta == null -> null
        else -> {
            val byId = rows.associateBy { it.id }.toMutableMap()
            (delta.added + delta.updated).forEach { byId[it.id] = it }
            delta.removedIds.forEach(byId::remove)
            byId.values.toList()
        }
    }
}

private class MalformedPush : IllegalArgumentException("Workspace update has no data")
