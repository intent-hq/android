package dev.intent.app.terminals

import dev.intent.core.protocol.models.TerminalAck
import dev.intent.core.protocol.models.TerminalData
import dev.intent.core.protocol.models.TerminalInfo
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalState(
    val terminals: List<TerminalInfo> = emptyList(),
    val selected: String? = null,
    val frame: TerminalFrame = TerminalFrame(),
    val loading: Boolean = true,
    val connected: Boolean = false,
    val creating: Boolean = false,
    val deleting: String? = null,
    val actionError: String? = null,
    val ready: Boolean = false,
    val sending: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

/** One workspace subscription, with fenced reads and no input replay on reconnect. */
@Suppress("TooManyFunctions", "ReturnCount")
class TerminalStore(private val session: TerminalSession) : Closeable {
    private val mutableState = MutableStateFlow(TerminalState())
    val state = mutableState.asStateFlow()
    private val renderer = AnsiTerminal()
    private val replay = TerminalReplay()
    private var lease: Closeable? = null
    private var closed = false
    private var generation = 0L
    private var boot: String? = null
    private var reading = false
    private val pending = mutableListOf<TerminalData>()
    private var pendingSize = 0
    private var columns = INITIAL_TERMINAL_COLUMNS
    private var rows = INITIAL_TERMINAL_ROWS

    @Synchronized
    fun start() {
        if (!closed &&
            lease == null
        ) {
            lease = session.subscribe(::receive, ::exit, ::refresh) { fail() }
        }
    }

    @Synchronized
    fun refresh() {
        if (closed) return
        val token = ++generation
        reading = true
        pending.clear()
        pendingSize = 0
        mutableState.value =
            state.value.copy(
                loading = true,
                connected = false,
                ready = false,
                sending = false,
                error = null
            )
        session.list().whenComplete { result, error ->
            synchronized(this) {
                if (!closed && token == generation) {
                    if (error != null) {
                        fail()
                    } else {
                        val selected = state.value.selected?.takeIf { id ->
                            result.terminals.any {
                                it.id ==
                                    id
                            }
                        }
                        if (boot != result.daemonBootId) {
                            renderer.reset()
                            replay.reset(result.daemonBootId)
                            pending.clear()
                            pendingSize = 0
                        }
                        boot = result.daemonBootId
                        mutableState.value = state.value.copy(
                            terminals = result.terminals,
                            selected = selected,
                            loading = false,
                            connected = true,
                            ended = false,
                            frame = renderer.frame()
                        )
                        if (selected != null) snapshot(selected, token) else reading = false
                    }
                }
            }
        }
    }

    @Synchronized
    fun create() {
        if (!canMutate()) return
        val token = generation
        mutableState.value = state.value.copy(creating = true, actionError = null)
        session.list().thenCompose { result ->
            synchronized(this) {
                check(!closed && token == generation && result.daemonBootId == boot)
                session.create()
            }
        }.whenComplete { result, error ->
            synchronized(this) {
                if (!closed) {
                    mutableState.value = state.value.copy(creating = false)
                    if (error != null || result.terminalId.isBlank()) {
                        mutableState.value = state.value.copy(
                            actionError = "Cannot create the terminal. Refresh before trying again."
                        )
                    } else if (token == generation) {
                        mutableState.value = state.value.copy(
                            terminals =
                            state.value.terminals.filterNot { it.id == result.terminalId } +
                                TerminalInfo(result.terminalId),
                            loading = false
                        )
                        select(result.terminalId)
                    } else {
                        // Never replay a create after reconnect or steal a changed selection.
                        refresh()
                    }
                }
            }
        }
    }

    @Synchronized
    fun delete(id: String) {
        if (!canMutate() || state.value.terminals.none { it.id == id }) return
        val token = generation
        mutableState.value = state.value.copy(deleting = id, actionError = null)
        session.list().thenCompose { result ->
            synchronized(this) {
                check(!closed && token == generation)
                if (result.daemonBootId != boot || result.terminals.none { it.id == id }) {
                    CompletableFuture.completedFuture(
                        TerminalAck(true)
                    )
                } else {
                    session.kill(id)
                }
            }
        }.whenComplete { result, error ->
            synchronized(this) {
                if (!closed) {
                    mutableState.value = state.value.copy(deleting = null)
                    if (error != null || !result.ok) {
                        mutableState.value = state.value.copy(
                            actionError = "Cannot delete the terminal. Refresh before trying again."
                        )
                    } else {
                        if (state.value.selected == id) select(null)
                        // Fence any list or buffer read started before the deletion.
                        generation++
                        mutableState.value = state.value.copy(
                            terminals = state.value.terminals.filterNot { it.id == id }
                        )
                        refresh()
                    }
                }
            }
        }
    }

    private fun canMutate() = !closed &&
        state.value.connected &&
        !state.value.loading &&
        !state.value.creating &&
        state.value.deleting == null

    @Synchronized
    fun select(id: String?) {
        if (closed || (state.value.loading && id != null)) return
        if (id != null && state.value.terminals.none { it.id == id }) return
        val token = ++generation
        pending.clear()
        pendingSize = 0
        renderer.reset()
        replay.reset(boot ?: return)
        reading = id != null
        mutableState.value = state.value.copy(
            selected = id,
            frame = renderer.frame(),
            ready = false,
            sending = false,
            ended = false,
            error = null,
            notice = null
        )
        if (id != null) snapshot(id, token)
    }

    private fun snapshot(id: String, token: Long) {
        reading = true
        mutableState.value = state.value.copy(ready = false)
        session.buffer(id).whenComplete { result, error ->
            synchronized(this) {
                if (!closed && token == generation && state.value.selected == id) {
                    if (error != null) {
                        fail()
                    } else {
                        runCatching {
                            require(result.terminalId == id)
                            require(result.daemonBootId == null || result.daemonBootId == boot)
                            val applied = replay.accept(
                                TerminalRange(
                                    result.data,
                                    result.daemonBootId,
                                    result.startOffset,
                                    result.endOffset
                                ),
                                true
                            )
                            render(applied)
                            val queued = pending.toList()
                            pending.clear()
                            pendingSize = 0
                            reading = false
                            mutableState.value =
                                state.value.copy(ready = !state.value.ended, error = null)
                            // Legacy snapshots cannot identify overlap. Discard queued bytes and disclose this limit.
                            if (!applied.legacy) {
                                queued.forEach {
                                    receiveRange(it, recover = false)
                                }
                            }
                            if (state.value.ready) sendSize(id, token)
                        }.onFailure { fail() }
                    }
                }
            }
        }
    }

    @Synchronized
    private fun receive(data: TerminalData) = receiveRange(data, recover = true)

    private fun receiveRange(data: TerminalData, recover: Boolean) {
        if (closed || data.terminalId != state.value.selected) return
        if (data.daemonBootId != null && data.daemonBootId != boot) return
        if (reading) {
            if (pendingSize + data.chunk.length > MAX_PENDING) {
                fail("Output exceeded the receive limit. Refresh the terminal.")
            } else {
                pending.add(data)
                pendingSize += data.chunk.length
            }
            return
        }
        if (!state.value.ready) return
        runCatching {
            val applied = replay.accept(
                TerminalRange(data.chunk, data.daemonBootId, data.startOffset, data.endOffset),
                false
            )
            if (applied.gap && !recover) {
                fail("Output has a gap. Refresh the terminal to recover available output.")
            } else if (applied.gap) {
                pending.add(data)
                pendingSize += data.chunk.length
                snapshot(data.terminalId, generation)
            } else {
                render(applied)
            }
        }.onFailure { fail("Terminal output was invalid. Refresh the terminal.") }
    }

    private fun render(result: ReplayResult) {
        if (result.reset) renderer.reset()
        renderer.feed(result.bytes)
        mutableState.value = state.value.copy(
            frame = renderer.frame(),
            notice = when {
                result.truncated -> "Earlier output is no longer available."
                result.legacy -> "No output cursors. Reconnect can lose output."
                else -> state.value.notice
            }
        )
    }

    @Synchronized
    private fun exit(id: String) {
        if (closed || id != state.value.selected) return
        val token = ++generation
        pending.clear()
        pendingSize = 0
        mutableState.value = state.value.copy(ended = true, ready = false, sending = false)
        snapshot(id, token)
    }

    @Synchronized
    fun send(text: String) {
        val current = state.value
        val id = current.selected ?: return
        if (closed || !current.ready) return
        if (current.sending || text.isEmpty()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_INPUT) {
            mutableState.value =
                current.copy(error = "Input is too large. Send a smaller selection.")
            return
        }
        val token = generation
        mutableState.value = current.copy(sending = true, error = null)
        session.write(id, bytes).whenComplete { result, error ->
            synchronized(this) {
                if (!closed && token == generation) {
                    if (error != null || !result.ok) {
                        fail(
                            "Input was not confirmed. Refresh before sending again."
                        )
                    } else {
                        mutableState.value = state.value.copy(sending = false)
                    }
                }
            }
        }
    }

    @Synchronized
    fun resize(columns: Int, rows: Int) {
        val width = columns.coerceIn(1, MAX_COLUMNS)
        val height = rows.coerceIn(1, MAX_ROWS)
        if (closed || (this.columns == width && this.rows == height)) return
        this.columns = width
        this.rows = height
        renderer.resize(width, height)
        mutableState.value = state.value.copy(frame = renderer.frame())
        val id = state.value.selected ?: return
        if (!state.value.ready) return
        sendSize(id, generation)
    }

    private fun sendSize(id: String, token: Long) {
        session.resize(id, columns, rows).whenComplete { result, error ->
            synchronized(this) {
                val failed = error != null || !result.ok
                if (!closed && token == generation && failed) {
                    mutableState.value =
                        state.value.copy(error = "The terminal size could not be updated.")
                }
            }
        }
    }

    @Synchronized
    private fun fail(message: String = "Cannot load the terminal. Refresh to try again.") {
        if (closed) return
        generation++
        reading = false
        pending.clear()
        pendingSize = 0
        mutableState.value =
            state.value.copy(
                loading = false,
                connected = false,
                ready = false,
                sending = false,
                error = message
            )
    }

    @Synchronized
    override fun close() {
        closed = true
        generation++
        lease?.close()
        lease = null
        pending.clear()
    }
}

private const val MAX_PENDING = 2_000_000
private const val MAX_INPUT = 65_536
private const val MAX_COLUMNS = 240
private const val MAX_ROWS = 100
