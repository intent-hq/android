package dev.intent.tailcat

import java.io.Closeable

/** Blocking tunnel operations. Call these off the Android main thread. */
interface TunnelClient : Closeable {
    /** Tests relay reachability. A nonpositive timeout uses the Go default (30 seconds). */
    fun ping(timeoutMs: Int)

    /** Forwards raw TCP only. Callers must verify pinned TLS before sending credentials. */
    fun forward(remotePort: Int): TunnelForwarder
}

interface TunnelForwarder : Closeable {
    /** The listener binds only to 127.0.0.1. */
    val localPort: Int
}

interface TunnelClientFactory {
    val available: Boolean

    fun createClient(token: String): TunnelClient
}

class TunnelUnavailableException : IllegalStateException("Tailcat is unavailable in this build.")

object UnavailableTunnelClientFactory : TunnelClientFactory {
    override val available = false

    override fun createClient(token: String): TunnelClient = throw TunnelUnavailableException()
}
