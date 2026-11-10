package dev.intent.app.scripts

import dev.intent.core.protocol.models.WorkspaceScriptResource
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkspaceScriptsState(
    val scripts: List<WorkspaceScriptResource> = emptyList(),
    val loading: Boolean = true,
    val connected: Boolean = false,
    val pending: Set<String> = emptySet(),
    val error: String? = null
)

/** A route owns one store; reconnect reloads reads but never replays start/stop. */
class WorkspaceScriptsStore(
    private val session: WorkspaceScriptSession,
    private val workspaceId: String
) : Closeable {
    private val mutableState = MutableStateFlow(WorkspaceScriptsState())
    val state = mutableState.asStateFlow()
    private var subscription: Closeable? = null
    private var closed = false
    private var generation = 0L
    private var revision = 0L
    private var reading = false
    private var dirty = false
    private var connectionReady = true
    private var admitted = false

    @Synchronized
    fun start(ready: Boolean = true) {
        if (closed || subscription != null) return
        connectionReady = ready
        if (!ready) invalidateConnection()
        subscription = session.subscribe(::refresh, ::connected, ::disconnected)
    }

    /** Ordinary transport loss does not invoke an admitted subscription's error callback. */
    @Synchronized
    fun connectionChanged(ready: Boolean) {
        if (closed || ready == connectionReady) return
        connectionReady = ready
        if (!ready) {
            admitted = false
            invalidateConnection()
        } else if (admitted) {
            activate()
        }
    }

    @Synchronized
    private fun connected() {
        if (closed) return
        admitted = true
        if (connectionReady) activate()
    }

    private fun activate() {
        generation++
        reading = false
        mutableState.value = state.value.copy(connected = true, pending = emptySet())
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (closed || !state.value.connected) return
        revision++
        if (reading) {
            dirty = true
            return
        }
        reading = true
        dirty = false
        val token = generation
        val readRevision = revision
        mutableState.value = state.value.copy(loading = true, error = null)
        session.list().whenComplete { rows, error ->
            synchronized(this) {
                if (!closed && token == generation) {
                    reading = false
                    if (dirty || readRevision != revision) {
                        refresh()
                    } else if (error != null) {
                        mutableState.value = state.value.copy(loading = false, error = LOAD_ERROR)
                    } else {
                        mutableState.value = state.value.copy(
                            scripts = rows.filter {
                                it.workspaceId == workspaceId &&
                                    it.id.isNotBlank()
                            }
                                .distinctBy { it.id },
                            loading = false,
                            error = null
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    fun setRunning(id: String, running: Boolean) {
        val current = state.value
        if (!canSetRunning(current, id, running)) return
        val token = generation
        mutableState.value = current.copy(pending = current.pending + id, error = null)
        // Confirm with a status read; do not claim success from an optimistic local state.
        session.setRunning(id, running).thenCompose { result ->
            check(result.ok && result.scriptId == id)
            session.status(id)
        }.whenComplete { _, error ->
            synchronized(this) {
                if (!closed && token == generation) {
                    mutableState.value = state.value.copy(pending = state.value.pending - id)
                    if (error != null) {
                        mutableState.value = state.value.copy(
                            error = "Script action was not confirmed. " +
                                "Refresh its status before trying again."
                        )
                    } else {
                        refresh()
                    }
                }
            }
        }
    }

    private fun canSetRunning(
        current: WorkspaceScriptsState,
        id: String,
        running: Boolean
    ): Boolean {
        val runtime = current.scripts.firstOrNull { it.id == id }?.runtime ?: return false
        val available = !closed && current.connected && !current.loading
        val actionable = id !in current.pending && running != runtime.isLive
        return available && actionable
    }

    @Synchronized
    private fun disconnected(@Suppress("UNUSED_PARAMETER") error: Throwable) {
        if (closed) return
        admitted = false
        invalidateConnection()
    }

    private fun invalidateConnection() {
        generation++
        reading = false
        dirty = false
        mutableState.value = state.value.copy(
            loading = false,
            connected = false,
            pending = emptySet(),
            error = "Script updates are unavailable. Reconnect to refresh their status."
        )
    }

    @Synchronized
    override fun close() {
        closed = true
        generation++
        subscription?.close()
        subscription = null
    }
}

private const val LOAD_ERROR = "Cannot load scripts. Refresh to try again."
