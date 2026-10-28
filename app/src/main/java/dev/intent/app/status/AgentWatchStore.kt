package dev.intent.app.status

import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One conversation lease. Events during a read coalesce into one fresh read. */
class AgentWatchStore(private val source: AgentWatchSource, private val agentId: String) :
    Closeable {
    private val mutable = MutableStateFlow(AgentWatches())
    val state = mutable.asStateFlow()
    private var lease: Closeable? = null
    private var closed = false
    private var revision = 0L
    private var reading = false
    private var dirty = false

    @Synchronized fun start() {
        if (closed || lease != null) return
        lease = source.subscribe({ id ->
            synchronized(this) {
                if (reading || id == agentId || mutable.value.rows.any { it.agent.id == id }) {
                    refresh()
                }
            }
        }, ::refresh, {
            synchronized(this) {
                if (!closed) {
                    revision++
                    reading = false
                    dirty = false
                    mutable.value = mutable.value.copy(error = "Agent watches unavailable. Retry.")
                }
            }
        })
    }

    @Synchronized fun refresh() {
        if (closed) return
        if (reading) {
            dirty = true
            return
        }
        revision++
        reading = true
        dirty = false
        val requested = revision
        source.read().whenComplete { result, failure ->
            synchronized(this) {
                if (!closed && requested == revision) {
                    reading = false
                    if (dirty) {
                        refresh()
                    } else {
                        mutable.value =
                            if (failure ==
                                null
                            ) {
                                result
                            } else {
                                mutable.value.copy(error = "Agent watches unavailable. Retry.")
                            }
                    }
                }
            }
        }
    }

    @Synchronized override fun close() {
        closed = true
        revision++
        lease?.close()
        lease = null
    }
}
