package dev.intent.app.connection

import dev.intent.core.protocol.CertificateFingerprint
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.SessionTransport
import dev.intent.core.protocol.session.pinnedSessionTransport
import dev.intent.tailcat.Tailcat
import dev.intent.tailcat.TunnelClientFactory
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal typealias RouteSocketFactory = (String, WssTransport.Listener) -> SessionTransport

/** Each candidate must finish pinned TLS and the authenticated upgrade before it can win. */
internal class HostRacingTransport(
    private val payload: PairingPayload,
    private val listener: WssTransport.Listener,
    private val tunnels: TunnelClientFactory = Tailcat,
    private val sockets: RouteSocketFactory = { endpoint, callbacks ->
        pinnedSessionTransport(
            endpoint,
            payload.token,
            requireNotNull(payload.certFingerprint)
        )(callbacks)
    },
    private val timeoutMillis: Long = 15_000,
    private val onRoute: (ConnectionPath) -> Unit = {}
) : SessionTransport {
    private val events = Executors.newSingleThreadScheduledExecutor()
    private val closed = AtomicBoolean()
    private val started = AtomicBoolean()
    private val candidates = mutableListOf<Candidate>()

    @Volatile private var winner: Candidate? = null
    val endpoint: String? get() = if (closed.get()) null else winner?.endpoint

    init {
        CertificateFingerprint.normalize(requireNotNull(payload.certFingerprint))
        require(timeoutMillis > 0)
    }

    override fun connect() {
        check(!closed.get() && started.compareAndSet(false, true))
        dispatch {
            payload.hosts.distinct().forEachIndexed { index, host ->
                candidates.add(
                    Candidate(
                        connectionEndpoint(host, payload.port),
                        "direct-$index",
                        directPath(host, payload.port)
                    )
                )
            }
            if (payload.tcAddress != null &&
                tunnels.available
            ) {
                candidates.add(
                    Candidate(null, "tunnel", tunnelPath(requireNotNull(payload.tcAddress)))
                )
            }
            if (payload.tcAddress != null && !tunnels.available) {
                RouteDiagnostics.record("tunnel", "candidate", "unavailable")
            }
            if (candidates.isEmpty()) {
                listener.onFailure(IOException("No available connection route"))
            } else {
                candidates.forEach(::start)
                events.schedule({
                    if (!closed.get() && winner == null) {
                        candidates.filterNot { it.failed }.forEach {
                            RouteDiagnostics.record(it.route, "race", "timeout")
                            failed(it, IOException("Connection route timed out"))
                        }
                    }
                }, timeoutMillis, TimeUnit.MILLISECONDS)
            }
        }
    }

    override fun send(message: JsonRpcMessage): Boolean =
        !closed.get() && winner?.transport?.send(message) == true

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            events.execute {
                candidates.forEach { it.transport?.close() }
                events.shutdownNow()
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // A failed candidate must not abort other routes.
    private fun start(candidate: Candidate) {
        RouteDiagnostics.record(candidate.route, "attempt", "start")
        val callbacks = object : WssTransport.Listener {
            override fun onPinVerified(accepted: Boolean) {
                RouteDiagnostics.record(candidate.route, "pin", if (accepted) "pass" else "fail")
            }
            override fun onOpen() = dispatch { opened(candidate) }
            override fun onMessage(message: JsonRpcMessage) = dispatch {
                if (winner === candidate) listener.onMessage(message)
            }
            override fun onClosed(code: Int, reason: String) = dispatch {
                failed(candidate, IOException("Connection route closed"))
            }
            override fun onFailure(error: Throwable) = dispatch { failed(candidate, error) }
        }
        try {
            val direct = candidate.endpoint
            candidate.transport = if (direct != null) {
                sockets(direct, callbacks)
            } else {
                TailcatSessionTransport(
                    requireNotNull(payload.tcAddress),
                    payload.port,
                    tunnels,
                    callbacks,
                    sockets
                ) { candidate.endpoint = it }
            }
            candidate.transport?.connect()
        } catch (error: Exception) {
            failed(candidate, error)
        }
    }

    private fun opened(candidate: Candidate) {
        if (candidate.failed || winner != null) return
        winner = candidate
        RouteDiagnostics.record(candidate.route, "race", "winner")
        candidates.filter { it !== candidate }.forEach {
            it.failed = true
            it.transport?.close()
        }
        onRoute(candidate.path)
        listener.onOpen()
    }

    private fun failed(candidate: Candidate, error: Throwable) {
        if (candidate.failed) return
        candidate.failed = true
        RouteDiagnostics.record(candidate.route, "attempt", "failure", error)
        candidate.transport?.close()
        if (winner === candidate || (winner == null && candidates.all { it.failed })) {
            listener.onFailure(error)
        }
    }

    private fun dispatch(action: () -> Unit) {
        try {
            events.execute { if (!closed.get()) action() }
        } catch (_: RejectedExecutionException) {
            // Late callbacks from cancelled candidates belong to a retired race.
        }
    }

    private class Candidate(
        @Volatile var endpoint: String?,
        val route: String,
        val path: ConnectionPath
    ) {
        var transport: SessionTransport? = null
        var failed = false
    }
}
