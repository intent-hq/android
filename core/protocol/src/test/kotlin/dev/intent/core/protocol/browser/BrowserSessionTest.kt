package dev.intent.core.protocol.browser

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionTest {
    @Test
    fun `session requests capabilities then closes browser on disconnect`() {
        val servers = LinkedBlockingQueue<RpcPeer>()
        val tunnel = FakeTunnel()
        RpcSession(
            { RpcPeer(it).also(servers::add) },
            JsonObject(emptyMap()),
            reconnectDelay = { 60_000 },
            browserTransportFactory = tunnel::create
        ).use { session ->
            session.connect()
            val peer = servers.poll(5, TimeUnit.SECONDS)!!
            peer.reply(peer.next(), JsonObject(emptyMap()))
            val browser = session.openBrowser()
            val status = peer.next()
            assertEquals("system.status", status.method)
            peer.reply(status, JsonObject(mapOf("protocolVersion" to JsonPrimitive("10.4"))))
            val proxy = browser.get(5, TimeUnit.SECONDS)
            assertTrue(
                proxy.forward(
                    "http://daemon.localhost:3000/"
                ).get(5, TimeUnit.SECONDS).startsWith("http://127.0.0.1:")
            )
            peer.listener.onFailure(IOException("offline"))
            assertTrue(tunnel.closedSignal.poll(5, TimeUnit.SECONDS)!!)
            assertTrue(proxy.forward("http://localhost:3000/").isCompletedExceptionally)
        }
    }

    @Test
    fun `disconnect during capability read never creates a tunnel`() {
        val servers = LinkedBlockingQueue<RpcPeer>()
        var created = false
        RpcSession(
            { RpcPeer(it).also(servers::add) },
            JsonObject(emptyMap()),
            reconnectDelay = { 60_000 },
            browserTransportFactory = {
                created = true
                FakeTunnel().create(it)
            }
        ).use { session ->
            session.connect()
            val peer = servers.poll(5, TimeUnit.SECONDS)!!
            peer.reply(peer.next(), JsonObject(emptyMap()))
            val browser = session.openBrowser()
            val status = peer.next()
            peer.listener.onFailure(IOException("offline"))
            assertThrows(java.util.concurrent.ExecutionException::class.java) {
                browser.get(5, TimeUnit.SECONDS)
            }
            peer.reply(status, JsonObject(emptyMap()))
            assertFalse(created)
        }
    }
}

private class RpcPeer(val listener: WssTransport.Listener) : SessionTransport {
    private val requests = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage): Boolean {
        requests.add(message as JsonRpcMessage.Request)
        return true
    }
    override fun close() = Unit
    fun next(): JsonRpcMessage.Request = checkNotNull(requests.poll(5, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, result: JsonObject) =
        listener.onMessage(JsonRpcMessage.Success(request.id, result))
}
