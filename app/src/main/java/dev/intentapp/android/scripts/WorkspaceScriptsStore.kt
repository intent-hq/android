package dev.intentapp.android.scripts

import dev.intent.core.protocol.browser.PreviewUrl
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row on the Scripts screen: a saved script, or a manifest script saved on first launch. */
data class WorkspaceScriptRow(
    val id: String,
    val workspaceId: String,
    val name: String,
    val command: String,
    val mode: String,
    val runtime: WorkspaceScriptRuntime?,
    val registered: Boolean = true
) {
    val active: Boolean get() = runtime?.isLive == true
    val status: String get() = runtime?.status ?: "idle"

    /** iOS WorkspaceScript.hasRun: the script ran at least once on the desktop. */
    val hasRun: Boolean get() = runtime?.let { it.status != "idle" || it.previouslyRunning } == true

    /** iOS WorkspaceScript.browser: only a running script opens its detected address. */
    val browserUrl: String?
        get() = runtime?.detectedUrl?.takeIf {
            status == "running" && runCatching { PreviewUrl.parse(it) }.isSuccess
        }
}

fun WorkspaceScriptResource.toRow() =
    WorkspaceScriptRow(id, workspaceId, name, command, mode, runtime)

data class ScriptOutputState(
    val scriptId: String,
    val text: String? = null,
    val loading: Boolean = true,
    val error: String? = null
)

data class WorkspaceScriptsState(
    val scripts: List<WorkspaceScriptResource> = emptyList(),
    val discovered: List<WorkspaceScriptRow> = emptyList(),
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val connected: Boolean = false,
    val pending: Set<String> = emptySet(),
    val launching: String? = null,
    val launchObserving: Boolean = false,
    val error: String? = null,
    val output: ScriptOutputState? = null,
    val openUrl: String? = null
) {
    /** iOS WorkspaceScriptStore.scripts: saved rows, then manifest rows not already saved. */
    val rows: List<WorkspaceScriptRow>
        get() {
            val saved = scripts.map { it.toRow() }
            val names = saved.map { it.name }.toSet()
            val commands = saved.map { it.command }.toSet()
            return saved + discovered.filter { it.name !in names && it.command !in commands }
        }

    fun isUpdating(id: String) = id in pending

    fun isLaunching(row: WorkspaceScriptRow) = launching == row.id &&
        (!launchObserving || row.status == "starting" || row.status == "restarting")
}

/** A route owns one store; reconnect reloads reads but never replays start/stop/remove. */
@Suppress("TooManyFunctions")
class WorkspaceScriptsStore(
    private val session: WorkspaceScriptSession,
    private val workspaceId: String
) : Closeable {
    private val mutableState = MutableStateFlow(WorkspaceScriptsState())
    val state = mutableState.asStateFlow()
    private var subscription: Closeable? = null
    private var closed = false
    private var generation = 0L
    private var launchToken = 0L
    private var revision = 0L
    private var outputRevision = 0L
    private var reading = false
    private var dirty = false
    private var connectionReady = true
    private var admitted = false

    private var value: WorkspaceScriptsState
        get() = mutableState.value
        set(next) {
            mutableState.value = next
        }

    @Synchronized
    fun start(ready: Boolean = true) {
        if (closed || subscription != null) return
        connectionReady = ready
        if (!ready) invalidateConnection()
        subscription = session.subscribe(::changed, ::connected, ::disconnected)
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
        value = value.copy(connected = true, pending = emptySet(), error = null)
        reload()
        value.output?.let { showOutput(it.scriptId) }
    }

    @Synchronized
    private fun changed() {
        refresh()
        value.output?.let { refreshOutput() }
    }

    /** Pull-to-refresh and Retry also re-read the workspace manifests. */
    @Synchronized
    fun reload() {
        if (closed || !value.connected) return
        val token = generation
        session.discover().whenComplete { rows, _ ->
            synchronized(this) {
                if (!closed && token == generation && rows != null) {
                    value = value.copy(discovered = rows)
                }
            }
        }
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (closed || !value.connected) return
        revision++
        if (reading) {
            dirty = true
            return
        }
        reading = true
        dirty = false
        val token = generation
        val readRevision = revision
        value = value.copy(loading = true)
        session.list().whenComplete { rows, error ->
            synchronized(this) {
                if (closed || token != generation) return@whenComplete
                reading = false
                when {
                    dirty || readRevision != revision -> refresh()
                    error != null -> value = value.copy(loading = false, error = LOAD_ERROR)
                    else -> {
                        val scripts = rows.filter {
                            it.workspaceId == workspaceId && it.id.isNotBlank()
                        }.distinctBy { it.id }
                        value = value.copy(
                            scripts = scripts,
                            loading = false,
                            loaded = true,
                            error = value.error.takeUnless { it == LOAD_ERROR }
                        )
                        reconcileLaunch()
                    }
                }
            }
        }
    }

    /** iOS WorkspaceScriptStore.launch: save a manifest script, start it, then open its address. */
    @Synchronized
    fun launch(id: String) {
        val current = value
        val row = current.rows.firstOrNull { it.id == id } ?: return
        if (!canAct(current, id) || current.launching == id) return
        val token = generation
        val launch = ++launchToken
        val isCurrent = { !closed && token == generation && launch == launchToken }
        var launchId = id
        value = current.copy(launching = id, launchObserving = false, error = null, openUrl = null)
        registered(row).thenCompose { saved ->
            synchronized(this) {
                check(isCurrent() && value.launching == id) { "Launch superseded" }
                launchId = saved.id
                adopt(saved)
            }
            startIfIdle(saved.id) {
                synchronized(this) { isCurrent() && value.launching == saved.id }
            }
                .thenApply { saved.id to it }
        }.whenComplete { result, error ->
            synchronized(this) {
                if (!isCurrent() || value.launching != launchId) return@whenComplete
                if (error != null || result == null) {
                    value = value.copy(launching = null, error = "Cannot start ${row.name}.")
                } else {
                    apply(result.first, result.second)
                    value = value.copy(launchObserving = true)
                    reconcileLaunch()
                }
                refresh()
            }
        }
    }

    private fun canAct(current: WorkspaceScriptsState, id: String) =
        !closed && current.connected && !current.isUpdating(id)

    private fun adopt(saved: WorkspaceScriptResource) {
        value = value.copy(launching = saved.id)
        if (value.scripts.none { it.id == saved.id }) {
            value = value.copy(scripts = value.scripts + saved)
        }
    }

    /** A live script is not started again; the confirmed status follows the acknowledgement. */
    private fun startIfIdle(
        id: String,
        stillLaunching: () -> Boolean
    ): CompletableFuture<WorkspaceScriptRuntime> = session.status(id).thenCompose { runtime ->
        if (runtime.isLive) {
            CompletableFuture.completedFuture(runtime)
        } else {
            // A newer launch may have superseded this one while the status read was in flight.
            check(stillLaunching()) { "Launch superseded" }
            session.setRunning(id, true).thenCompose { result ->
                check(result.ok && result.scriptId == id)
                session.status(id)
            }
        }
    }

    /** iOS WorkspaceScriptRepository.register: reuse a saved script with the same name or command. */
    private fun registered(row: WorkspaceScriptRow): CompletableFuture<WorkspaceScriptResource> {
        if (row.registered) {
            val saved = value.scripts.first { it.id == row.id }
            return CompletableFuture.completedFuture(saved)
        }
        return session.list().thenCompose { scripts ->
            val mine = scripts.filter { it.workspaceId == workspaceId }
            val existing = mine.firstOrNull { it.name == row.name }
                ?: mine.firstOrNull { it.command == row.command }
            existing?.let { CompletableFuture.completedFuture(it) }
                ?: session.create(row.name, row.command, row.mode).thenApply {
                    check(it.workspaceId == workspaceId && it.id.isNotBlank())
                    it
                }
        }
    }

    private fun reconcileLaunch() {
        val id = value.launching ?: return
        if (!value.launchObserving) return
        val row = value.rows.firstOrNull { it.id == id }
        when {
            row == null -> value = value.copy(launching = null)
            row.browserUrl != null -> value = value.copy(launching = null, openUrl = row.browserUrl)
            !row.active -> value = value.copy(launching = null, error = row.runtime?.error)
        }
    }

    @Synchronized
    fun consumeOpenUrl() {
        value = value.copy(openUrl = null)
    }

    @Synchronized
    fun stop(id: String) {
        val row = value.rows.firstOrNull { it.id == id } ?: return
        if (!row.active && value.launching != id) return
        perform(row) {
            session.setRunning(id, false).thenCompose { result ->
                check(result.ok && result.scriptId == id)
                session.status(id)
            }.thenApply { runtime -> { apply(id, runtime) } }
        }
    }

    @Synchronized
    fun remove(id: String) {
        val row = value.rows.firstOrNull { it.id == id } ?: return
        perform(row) {
            session.remove(id).thenApply { result ->
                check(result.ok && result.scriptId == id)
                val update: () -> Unit = {
                    value = value.copy(
                        scripts = value.scripts.filterNot { it.id == id },
                        output = value.output?.takeUnless { it.scriptId == id }
                    )
                }
                update
            }
        }
    }

    private fun perform(row: WorkspaceScriptRow, request: () -> CompletableFuture<() -> Unit>) {
        val current = value
        if (!row.registered || !canAct(current, row.id)) return
        val token = generation
        value = current.copy(
            pending = current.pending + row.id,
            error = null,
            launching = current.launching.takeUnless { it == row.id }
        )
        request().whenComplete { update, error ->
            synchronized(this) {
                if (closed || token != generation) return@whenComplete
                value = value.copy(pending = value.pending - row.id)
                if (error != null || update == null) {
                    value = value.copy(error = "Cannot update ${row.name}. Refresh and try again.")
                } else {
                    update()
                }
                refresh()
            }
        }
    }

    private fun apply(id: String, runtime: WorkspaceScriptRuntime) {
        value = value.copy(
            scripts = value.scripts.map { if (it.id == id) it.copy(runtime = runtime) else it }
        )
    }

    @Synchronized
    fun showOutput(id: String) {
        if (closed) return
        value = value.copy(output = ScriptOutputState(id))
        refreshOutput()
    }

    @Synchronized
    fun hideOutput() {
        outputRevision++
        value = value.copy(output = null)
    }

    @Synchronized
    fun refreshOutput() {
        val output = value.output ?: return
        if (closed || !value.connected) {
            value = value.copy(output = output.copy(loading = false, error = OUTPUT_OFFLINE))
            return
        }
        val token = generation
        val readRevision = ++outputRevision
        value = value.copy(output = output.copy(loading = true, error = null))
        session.output(output.scriptId).whenComplete { text, error ->
            synchronized(this) {
                val current = value.output
                if (closed || token != generation || readRevision != outputRevision) {
                    return@whenComplete
                }
                if (current?.scriptId != output.scriptId) return@whenComplete
                value = value.copy(
                    output = if (error != null) {
                        current.copy(loading = false, error = OUTPUT_ERROR)
                    } else {
                        current.copy(text = cleanScriptOutput(text), loading = false, error = null)
                    }
                )
            }
        }
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
        value = value.copy(
            loading = false,
            connected = false,
            pending = emptySet(),
            launching = null,
            output = value.output?.copy(loading = false),
            error = DISCONNECTED
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

/** Script logs are PTY output: drop terminal control sequences and carriage-return redraws. */
internal fun cleanScriptOutput(text: String): String = text
    .replace(OSC_SEQUENCE, "")
    .replace(CSI_SEQUENCE, "")
    .replace("\r\n", "\n")
    .split("\n")
    .joinToString("\n") { it.substringAfterLast('\r') }
    .filter { it == '\n' || it == '\t' || !it.isISOControl() }
    .trimEnd()

private val OSC_SEQUENCE = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
private val CSI_SEQUENCE = Regex("\u001B(?:\\[[0-?]*[ -/]*[@-~]|[@-Z\\\\-_])")

private const val LOAD_ERROR = "Cannot load scripts."
private const val OUTPUT_ERROR = "Cannot load output."
private const val OUTPUT_OFFLINE = "Reconnect to load output."
private const val DISCONNECTED = "Scripts are unavailable while disconnected."
