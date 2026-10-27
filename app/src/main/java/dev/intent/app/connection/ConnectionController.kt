package dev.intent.app.connection

import dev.intent.core.protocol.CertificateFingerprint
import dev.intent.core.protocol.pairing.ManualPairingInput
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI snapshots never expose a bearer token as a label or error. */
data class ConnectionState(
    val connections: List<SavedConnection> = emptyList(),
    val activeId: String? = null,
    val status: String = "Not connected",
    val pending: PairingPayload? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val session: RpcSession? = null,
    val cacheScope: String? = null,
    val activePath: ConnectionPath? = null
)

/** Serializes storage, pairing, and session replacement; stale callbacks cannot admit another server. */
@Suppress("TooManyFunctions")
class ConnectionController(
    private val repository: ConnectionRepository,
    private val sessionFactory: ((SavedConnection, (SessionState) -> Unit) -> RpcSession)? = null,
    private val probe: (
        String,
        Int
    ) -> String = { host, port -> CertificateFingerprint.probe(host, port) }
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean()
    private val mutableState = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = mutableState.asStateFlow()
    private var current: RpcSession? = null
    private var generation = 0L
    private var admission = 0L
    private var selectedPath: ConnectionPath? = null
    private var namedAdmission = -1L
    val diagnostics = ConnectionDiagnostics()

    fun restore() = submit {
        val rows = repository.list()
        mutableState.value = state.value.copy(connections = rows)
        rows.firstOrNull { it.id == repository.activeId() }?.let(::activate)
    }

    fun prepareLink(code: String) = submit {
        prepare(requireNotNull(PairingPayload.parse(code)))
    }

    fun prepareManual(input: ManualPairingInput) = submit { prepare(input.toPayload()) }

    private fun prepare(payload: PairingPayload) {
        mutableState.value = state.value.copy(pending = null, busy = true, error = null)
        require(payload.hosts.isNotEmpty()) { "A direct host is required" }
        val fingerprint = payload.certFingerprint?.let(CertificateFingerprint::normalize)
        mutableState.value = state.value.copy(
            pending = payload.copy(certFingerprint = fingerprint),
            busy = false
        )
    }

    fun probeCertificate() = submit {
        val payload = state.value.pending ?: return@submit
        mutableState.value = state.value.copy(busy = true)
        val fingerprint = probe(payload.hosts.first().removeSurrounding("[", "]"), payload.port)
        mutableState.value = state.value.copy(
            pending = payload.copy(certFingerprint = fingerprint),
            busy = false
        )
    }

    fun cancelPairing() = submit {
        mutableState.value =
            state.value.copy(pending = null, error = null)
    }

    fun confirmPairing() = submit {
        val payload = state.value.pending ?: return@submit
        requireNotNull(payload.certFingerprint)
        val connection = repository.add(payload)
        mutableState.value = state.value.copy(pending = null, connections = repository.list())
        activate(connection)
    }

    fun select(id: String) = submit {
        activate(repository.list().first { it.id == id })
    }

    fun disconnect() = submit {
        repository.select(null)
        stopSession()
    }

    fun forget(id: String) = submit {
        if (state.value.activeId == id) stopSession()
        repository.remove(id)
        mutableState.value = state.value.copy(connections = repository.list())
    }

    private fun activate(connection: SavedConnection) {
        repository.select(connection.id)
        stopSession()
        val ticket = generation
        mutableState.value =
            state.value.copy(activeId = connection.id, status = "Connecting", error = null)
        val callback: (SessionState) -> Unit = { status ->
            submit {
                if (ticket == generation) sessionChanged(connection, status, ticket)
            }
        }
        current = sessionFactory?.invoke(connection, callback) ?: createConnectionSession(
            connection,
            callback,
            latestPayload = {
                repository.list().firstOrNull { it.id == connection.id }?.payload
                    ?: connection.payload
            },
            onRoute = { path -> submit { if (ticket == generation) selectedPath = path } }
        )
        current?.connect()
    }

    private fun sessionChanged(connection: SavedConnection, status: SessionState, ticket: Long) {
        if (ticket != generation) return
        admission++
        if (status == SessionState.CONNECTING ||
            status == SessionState.DISCONNECTED
        ) {
            selectedPath = null
        }
        diagnostics.record(ConnectionEvent.valueOf(status.name))
        if (status == SessionState.CLOSED) {
            stopSession()
            mutableState.value = state.value.copy(
                error = "Cannot verify identity. Reconnect to try again."
            )
            return
        }
        // Preserve the admitted session and Compose ownership during a transport retry.
        // RpcSession validates the same principal before restoring subscriptions.
        mutableState.value = state.value.copy(
            activePath = selectedPath.takeIf { status == SessionState.READY },
            status = when (status) {
                SessionState.READY -> "Connected"
                SessionState.CONNECTING, SessionState.HANDSHAKING -> "Connecting"
                else -> "Connection lost. Retrying."
            }
        )
        if (status == SessionState.READY) {
            val principal = current?.principalId
            if (principal.isNullOrBlank()) {
                stopSession()
            } else {
                mutableState.value = state.value.copy(
                    session = current,
                    cacheScope = connectionCacheScope(connection.payload, principal),
                    error = null
                )
                val saved = repository.list().firstOrNull { it.id == connection.id }
                if (saved != null) {
                    repository.updateMetadata(
                        saved,
                        saved.metadata.copy(lastConnectedAt = System.currentTimeMillis())
                    )
                }
                mutableState.value = state.value.copy(connections = repository.list())
                refreshName(connection.id, ticket, admission)
                refreshHosts(connection.id, ticket, admission)
            }
        }
    }

    private fun refreshName(id: String, ticket: Long, admitted: Long) {
        val session = current ?: return
        session.request("host.status").whenComplete { result, _ ->
            submit {
                if (ticket == generation && admitted == admission && current === session) {
                    val saved = repository.list().firstOrNull { it.id == id } ?: return@submit
                    val name = reportedConnectionName(result) ?: return@submit
                    if (repository.updateMetadata(saved, saved.metadata.copy(name = name))) {
                        namedAdmission = admitted
                        mutableState.value = state.value.copy(connections = repository.list())
                    }
                }
            }
        }
    }

    private fun refreshHosts(id: String, ticket: Long, admitted: Long) {
        val session = current ?: return
        val saved = repository.list().firstOrNull { it.id == id } ?: return
        session.request("system.status").whenComplete { result, error ->
            submit {
                if (ticket == generation && admitted == admission && current === session) {
                    if (error != null) {
                        diagnostics.record(ConnectionEvent.HOST_REFRESH_FAILED)
                    } else if (repository.refresh(saved, refreshedPayload(saved.payload, result))) {
                        val updated = repository.list().firstOrNull { it.id == id }
                        val name = reportedConnectionName(result, shared = true)
                        if (updated != null && namedAdmission != admitted && name != null) {
                            repository.updateMetadata(updated, updated.metadata.copy(name = name))
                        }
                        mutableState.value = state.value.copy(connections = repository.list())
                        diagnostics.record(ConnectionEvent.HOSTS_REFRESHED)
                    }
                }
            }
        }
    }

    private fun stopSession() {
        generation++
        selectedPath = null
        current?.close()
        current = null
        mutableState.value = state.value.copy(
            activeId = null,
            session = null,
            cacheScope = null,
            activePath = null,
            status = "Not connected"
        )
    }

    @Synchronized
    private fun submit(action: () -> Unit) {
        if (closed.get()) return
        executor.execute {
            try {
                action()
            } catch (_: Exception) {
                mutableState.value = state.value.copy(
                    busy = false,
                    pending = null,
                    error = "Cannot complete this action. Check the connection details."
                )
            }
        }
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute { stopSession() }
        executor.shutdown()
    }
}
