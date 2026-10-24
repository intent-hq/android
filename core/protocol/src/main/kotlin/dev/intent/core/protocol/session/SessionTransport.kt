package dev.intent.core.protocol.session

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import java.io.Closeable

/** A fresh transport is created for each physical connection. */
interface SessionTransport : Closeable {
    fun connect()
    fun send(message: JsonRpcMessage): Boolean
}

fun pinnedSessionTransport(
    endpoint: String,
    token: String,
    fingerprint: String,
    heartbeatMillis: Long = 30_000
): (WssTransport.Listener) -> SessionTransport = { listener ->
    val transport = WssTransport(endpoint, token, fingerprint, listener, heartbeatMillis)
    object : SessionTransport {
        override fun connect() = transport.connect()
        override fun send(message: JsonRpcMessage) = transport.send(message)
        override fun close() = transport.close()
    }
}
