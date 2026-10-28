package dev.intent.app.smoke

import dev.intent.core.protocol.browser.TunnelFrame
import dev.intent.core.protocol.browser.TunnelTransport
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** HTTP fixture behind the real BrowserTunnel framing and loopback listener. */
internal class PreviewTunnelServer(private val fail: Boolean) {
    val requests = CopyOnWriteArrayList<Pair<Int, String>>()

    fun create(listener: TunnelTransport.Listener): TunnelTransport = object : TunnelTransport {
        private val ports = ConcurrentHashMap<Int, Int>()
        private val buffers = ConcurrentHashMap<Int, String>()
        override fun connect() {
            if (fail) listener.failed() else listener.opened()
        }
        override fun close() = Unit
        override fun send(bytes: ByteArray): Boolean {
            val frame = TunnelFrame.decode(bytes)
            when (frame.opcode) {
                TunnelFrame.OPEN -> {
                    ports[frame.streamId] = ByteBuffer.wrap(frame.payload).short.toInt() and 0xffff
                    listener.received(TunnelFrame(TunnelFrame.OPEN_OK, frame.streamId).encode())
                }
                TunnelFrame.DATA -> {
                    val request = buffers.getOrDefault(frame.streamId, "") + String(frame.payload)
                    buffers[frame.streamId] = request
                    if (request.contains("\r\n\r\n")) respond(frame.streamId, request)
                }
            }
            return true
        }
        private fun respond(id: Int, request: String) {
            val port = ports.remove(id) ?: return
            requests.add(port to request)
            buffers.remove(id)
            val html = """<html><body><h1>Tunneled preview</h1>
                <a id="next" href="http://[::1]:4322/next">Next server</a></body></html>"""
            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
            listener.received(TunnelFrame(TunnelFrame.DATA, id, response.toByteArray()).encode())
            listener.received(TunnelFrame(TunnelFrame.EOF, id).encode())
        }
    }
}
