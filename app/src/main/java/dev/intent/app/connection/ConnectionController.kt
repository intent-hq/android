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
import kotlinx.serialization.json.JsonPrimitive

/** UI snapshots never expose a bearer token as a label or error. */
data class ConnectionState(
    val connections: List<SavedConnection> = emptyList(),
    val activeId: String? = null,
    val status: String = "Not connected",
    val pending: PairingPayload? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val session: RpcSession? = null,
    val cacheScope: String? = null
)

/** Serializes storage, pairing, and session replacement; stale callbacks cannot admit another server. */
@Suppress("TooManyFunctions")
class ConnectionController(
    private val repository: ConnectionRepository,
    private val sessionFactory: (SavedConnection, (SessionState) -> Unit) -> RpcSession =
        { connection, callback -> createConnectionSession(connection, callback) },
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
        current = sessionFactory(connection) { status ->
            submit {
                if (ticket == generation) sessionChanged(connection, status, ticket)
            }
        }
        current?.connect()
    }

    private fun sessionChanged(connection: SavedConnection, status: SessionState, ticket: Long) {
        val revision = ++admission
        mutableState.value = state.value.copy(
            status = when (status) {
                SessionState.READY -> "Checking server identity"
                SessionState.CONNECTING, SessionState.HANDSHAKING -> "Connecting"
                SessionState.DISCONNECTED -> "Connection lost. Retrying."
                SessionState.CLOSED -> "Not connected"
            },
            session = null,
            cacheScope = null
        )
        if (status == SessionState.READY) {
            current?.request("principal.me")?.whenComplete { result, error ->
                submit {
                    if (ticket == generation && revision == admission) {
                        val principal = (result?.get("id") as? JsonPrimitive)?.takeIf {
                            it.isString
                        }?.content
                        if (error != null || principal.isNullOrBlank()) {
                            stopSession()
                            mutableState.value =
                                state.value.copy(
                                    error = "Cannot verify identity. Reconnect to try again."
                                )
                        } else {
                            mutableState.value = state.value.copy(
                                status = "Connected",
                                session = current,
                                cacheScope = connectionCacheScope(connection.payload, principal),
                                error = null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopSession() {
        generation++
        admission++
        current?.close()
        current = null
        mutableState.value = state.value.copy(
            activeId = null,
            session = null,
            cacheScope = null,
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
