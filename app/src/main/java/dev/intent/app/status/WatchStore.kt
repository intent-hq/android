package dev.intent.app.status

import dev.intent.core.protocol.session.RpcException
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/** One workspace lease; event journals fence late pages and selected details. No timer performs RPC. */
@Suppress("TooManyFunctions")
class WatchStore(private val source: WatchSource) : Closeable {
    private val mutable = MutableStateFlow(WatchState())
    val state = mutable.asStateFlow()
    private val leases = mutableMapOf<WatchFamily, Closeable>()
    private val acknowledged = mutableSetOf<WatchFamily>()
    private val revisions = mutableMapOf<WatchFamily, Long>()
    private val journals = mutableMapOf<WatchFamily, MutableList<JsonObject>>()
    private var generation = 0L
    private var selection = 0L
    private var closed = false

    @Synchronized
    fun start() {
        if (closed || leases.isNotEmpty()) return
        attach(WatchFamily.HOOK)
        attach(WatchFamily.PR)
    }

    private fun attach(family: WatchFamily) {
        val current = generation
        leases[family] = source.subscribe(family, { event ->
            synchronized(this) { if (!closed && generation == current) event(family, event) }
        }, {
            synchronized(this) {
                if (!closed && generation == current) {
                    admitted(family)
                }
            }
        }, {
            synchronized(this) {
                if (!closed && generation == current) {
                    acknowledged.remove(family)
                    invalidateDetail(family)
                    revisions[family] = (revisions[family] ?: 0) + 1
                    journals.remove(family)
                    update(
                        family,
                        bucket(family).copy(loading = false, error = "Status unavailable. Retry.")
                    )
                }
            }
        })
    }

    private fun invalidateDetail(family: WatchFamily) {
        if (family != WatchFamily.SCRIPT) return
        selection++
        mutable.value = mutable.value.copy(
            selected = mutable.value.selected?.copy(
                detail = null,
                loading = false,
                error = "Script watch details are unavailable. Retry to reconnect."
            )
        )
    }

    private fun admitted(family: WatchFamily) {
        acknowledged.add(family)
        if (family == WatchFamily.SCRIPT && !source.scriptsSupported) {
            update(family, WatchBucket())
            dismiss()
        } else {
            refresh(family)
        }
        if (source.scriptsSupported &&
            WatchFamily.SCRIPT !in leases &&
            family != WatchFamily.SCRIPT
        ) {
            attach(WatchFamily.SCRIPT)
        }
        if (family == WatchFamily.SCRIPT && mutable.value.selected != null) loadDetail()
    }

    @Suppress("ReturnCount") // Explicit paging and subscription prerequisites.
    @Synchronized
    fun refresh(family: WatchFamily, more: Boolean = false) {
        if (closed || (family == WatchFamily.SCRIPT && !source.scriptsSupported)) return
        if (family !in acknowledged) {
            leases.remove(family)?.close()
            attach(family)
            return
        }
        val old = bucket(family)
        if (more && (old.loading || old.page.nextCursor == null)) return
        val revision = (revisions[family] ?: 0) + 1
        revisions[family] = revision
        val current = generation
        val journal = mutableListOf<JsonObject>()
        journals[family] = journal
        update(family, old.copy(loading = true, error = null))
        source.list(family, if (more) old.page.nextCursor else null).whenComplete { page, error ->
            synchronized(this) {
                if (!closed && generation == current && revisions[family] == revision) {
                    journals.remove(family)
                    receivePage(
                        family,
                        if (more) old.page.rows else emptyList(),
                        page,
                        error,
                        journal
                    )
                }
            }
        }
    }

    private fun receivePage(
        family: WatchFamily,
        retained: List<JsonObject>,
        page: WatchPage?,
        error: Throwable?,
        journal: List<JsonObject>
    ) {
        if (error != null || page == null) {
            update(
                family,
                bucket(family).copy(loading = false, error = "Status unavailable. Retry.")
            )
        } else {
            val rows = merge(family, retained, page.rows)
            val folded = journal.fold(rows) { result, event -> foldWatch(family, result, event) }
            update(family, WatchBucket(WatchPage(folded, page.nextCursor)))
            if (family != WatchFamily.SCRIPT && journal.isNotEmpty()) refresh(family)
        }
    }

    private fun event(family: WatchFamily, event: JsonObject) {
        if (family == WatchFamily.SCRIPT && !source.scriptsSupported) return
        journals[family]?.let { journal ->
            if (journal.size >= MAX_PENDING_EVENTS) refresh(family)
            journals[family]?.add(event)
        }
        update(
            family,
            bucket(family).copy(
                page = bucket(family).page.copy(
                    rows = foldWatch(family, bucket(family).page.rows, event)
                )
            )
        )
        if (family == WatchFamily.SCRIPT) {
            val selected = mutable.value.selected
            val row = event.objectValue("data")?.objectValue("monitor")
            val ended = event.text("type") in SCRIPT_TERMINAL_EVENTS
            val matches =
                selected != null &&
                    row?.text("monitorId") == selected.summary.text("monitorId") &&
                    row?.text("runId") == selected.summary.text("runId")
            if (matches && ended && selected != null) {
                selection++
                mutable.value =
                    mutable.value.copy(
                        selected = selected.copy(detail = null, loading = false, unavailable = true)
                    )
            }
        } else if (family !in journals) {
            refresh(family)
        }
    }

    @Synchronized
    fun prAction(id: String, action: PrMonitorAction) {
        if (closed || mutable.value.prActions[id]?.busy == true) return
        val active = bucket(WatchFamily.PR).page.rows.any {
            it.text("monitorId") == id && it.active(WatchFamily.PR)
        }
        if (!active) return
        val current = generation
        setPrAction(id, PrActionState(busy = true))
        source.prAction(id, action).whenComplete { _, error ->
            synchronized(this) {
                if (!closed && generation == current) {
                    setPrAction(
                        id,
                        PrActionState(
                            error = error?.let {
                                "${action.label} failed. Try again."
                            }
                        )
                    )
                    if (error == null) refresh(WatchFamily.PR)
                }
            }
        }
    }

    private fun setPrAction(id: String, state: PrActionState) {
        mutable.value = mutable.value.copy(prActions = mutable.value.prActions + (id to state))
    }

    @Synchronized
    fun select(row: JsonObject) {
        if (closed) return
        mutable.value = mutable.value.copy(selected = WatchDetail(row))
        loadDetail()
    }

    @Suppress("ReturnCount") // Details require an active selection and an admitted event feed.
    @Synchronized
    fun loadDetail() {
        val selected = mutable.value.selected ?: return
        if (closed || selected.unavailable || !source.scriptsSupported) return
        if (WatchFamily.SCRIPT !in acknowledged) {
            refresh(WatchFamily.SCRIPT)
            return
        }
        val current = ++selection
        mutable.value =
            mutable.value.copy(
                selected = selected.copy(detail = null, loading = true, error = null)
            )
        source.detail(requireNotNull(selected.summary.text("monitorId"))).whenComplete {
                row,
                error
            ->
            synchronized(this) {
                if (!closed && current == selection) {
                    val unavailable =
                        (
                            generateSequence(error) {
                                it.cause
                            }.filterIsInstance<RpcException>().firstOrNull()
                            )?.error?.code ==
                            INVALID_PARAMS
                    val valid = error == null && row.text("runId") == selected.summary.text("runId")
                    mutable.value = mutable.value.copy(
                        selected = selected.copy(
                            detail = row.takeIf { valid },
                            loading = false,
                            unavailable = unavailable,
                            error = if (!valid &&
                                !unavailable
                            ) {
                                "Script watch details are unavailable."
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun dismiss() {
        selection++
        mutable.value = mutable.value.copy(selected = null)
    }

    private fun bucket(family: WatchFamily) = mutable.value.buckets[family] ?: WatchBucket()
    private fun update(family: WatchFamily, bucket: WatchBucket) {
        mutable.value = mutable.value.copy(buckets = mutable.value.buckets + (family to bucket))
    }

    @Synchronized
    override fun close() {
        closed = true
        generation++
        selection++
        leases.values.forEach { it.close() }
        leases.clear()
        acknowledged.clear()
        journals.clear()
    }
}

internal fun merge(
    family: WatchFamily,
    old: List<JsonObject>,
    incoming: List<JsonObject>
): List<JsonObject> = (old + incoming).associateBy { it.text(family.idKey) }.values.toList()

@Suppress("ReturnCount") // Reject incomplete or unrelated events before folding them.
internal fun foldWatch(
    family: WatchFamily,
    rows: List<JsonObject>,
    event: JsonObject
): List<JsonObject> {
    val data = event.objectValue("data") ?: return rows
    val type = event.text("type") ?: return rows
    if (!type.startsWith("${family.wire}:")) return rows
    val row = if (family == WatchFamily.SCRIPT) data.objectValue("monitor") ?: return rows else data
    val id = row.text(family.idKey) ?: return rows
    val terminal =
        type.substringAfter(':') in
            setOf("completed", "expired", "triggered", "cancelled", "dispatched", "evicted")
    if (terminal) {
        return rows.filterNot {
            it.text(family.idKey) == id &&
                (family != WatchFamily.SCRIPT || it.text("runId") == row.text("runId"))
        }
    }
    if (family == WatchFamily.SCRIPT && type == "scriptMonitor:registered") {
        return merge(family, rows, listOf(JsonObject(row.filterKeys { it != "outputPattern" })))
    }
    return rows
}

private const val INVALID_PARAMS = -32602
private val SCRIPT_TERMINAL_EVENTS = setOf(
    "scriptMonitor:completed",
    "scriptMonitor:expired",
    "scriptMonitor:triggered",
    "scriptMonitor:cancelled"
)

private const val MAX_PENDING_EVENTS = 256
