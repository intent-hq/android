package dev.intent.core.protocol.browser

import dev.intent.core.protocol.WssTransport
import java.io.Closeable

interface TunnelTransport : Closeable {
    fun connect()
    fun send(bytes: ByteArray): Boolean
    interface Listener {
        fun opened()
        fun received(bytes: ByteArray)
        fun failed()
    }
}

fun pinnedTunnelTransport(
    endpoint: String,
    token: String,
    fingerprint: String
): (TunnelTransport.Listener) -> TunnelTransport = { listener ->
    val socket = WssTransport(
        endpoint,
        token,
        fingerprint,
        object : WssTransport.Listener {
            override fun onOpen() = listener.opened()
            override fun onBinary(bytes: ByteArray) = listener.received(bytes)
            override fun onClosed(code: Int, reason: String) = listener.failed()
            override fun onFailure(error: Throwable) = listener.failed()
        },
        binary = true
    )
    object : TunnelTransport {
        override fun connect() = socket.connect()
        override fun send(bytes: ByteArray) = socket.sendBinary(bytes)
        override fun close() = socket.close()
    }
}
