package dev.intent.app.connection

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.session.SessionTransport
import dev.intent.tailcat.TunnelClientFactory
import java.io.Closeable
import java.util.concurrent.Executors

/** Owns late native results too: cancellation never waits for a blocking Go call. */
internal class TailcatSessionTransport(
    private val address: String,
    private val remotePort: Int,
    private val factory: TunnelClientFactory,
    private val listener: WssTransport.Listener,
    private val sockets: RouteSocketFactory,
    private val onEndpoint: (String) -> Unit
) : SessionTransport {
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val resources = mutableListOf<Closeable>()
    private var closed = false

    @Volatile private var socket: SessionTransport? = null

    @Suppress("TooGenericExceptionCaught") // gomobile reports Go failures as Exception.
    override fun connect() {
        worker.execute {
            try {
                val client = factory.createClient(address)
                if (own(client)) {
                    client.ping(PING_TIMEOUT_MILLIS)
                    val forwarder = client.forward(remotePort)
                    if (own(forwarder)) {
                        require(forwarder.localPort in 1..MAX_PORT)
                        val endpoint = connectionEndpoint("127.0.0.1", forwarder.localPort)
                        val transport = sockets(endpoint, listener)
                        if (own(transport)) {
                            socket = transport
                            onEndpoint(endpoint)
                            transport.connect()
                        }
                    }
                }
            } catch (error: Exception) {
                listener.onFailure(error)
                close()
            } finally {
                worker.shutdown()
            }
        }
    }

    override fun send(message: JsonRpcMessage): Boolean = socket?.send(message) == true

    private fun own(resource: Closeable): Boolean {
        val accepted = synchronized(lock) {
            if (closed) {
                false
            } else {
                resources.add(resource)
                true
            }
        }
        if (!accepted) resource.close()
        return accepted
    }

    override fun close() {
        val retired = synchronized(lock) {
            if (closed) return
            closed = true
            resources.toList().asReversed().also { resources.clear() }
        }
        socket = null
        // Closing the Go client can block. Never hold the race or RPC executor here.
        val cleanup = Executors.newSingleThreadExecutor()
        cleanup.execute { retired.forEach { runCatching { it.close() } } }
        cleanup.shutdown()
        worker.shutdown()
    }

    private companion object {
        const val PING_TIMEOUT_MILLIS = 5000
        const val MAX_PORT = 65535
    }
}
