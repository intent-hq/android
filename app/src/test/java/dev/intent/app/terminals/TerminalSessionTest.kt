package dev.intent.app.terminals

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.TerminalData
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalSessionTest {
    @Test
    fun commandsKeepWorkspaceScopeAndBinaryFraming() {
        val transports = LinkedBlockingQueue<TerminalTransport>()
        RpcSession({
            TerminalTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val source = RpcTerminalSession(rpc, "ws")
            val terminals = source.list()
            server.reply(
                server.request(),
                """{"terminals":[{"id":"term","name":"Shell"}],"daemonBootId":"boot"}"""
            )
            assertEquals("Shell", terminals.get(2, TimeUnit.SECONDS).terminals.single().name)
            val snapshot = source.buffer("term")
            val read = server.request()
            assertEquals("terminal.getBuffer", read.method)
            assertFalse(read.params!!.jsonObject.containsKey("maxBytes"))
            server.reply(read, """{"terminalId":"term","data":"YQ=="}""")
            assertEquals("YQ==", snapshot.get(2, TimeUnit.SECONDS).data)
            val write = source.write("term", byteArrayOf(0, -1, 13))
            val input = server.request()
            assertEquals("terminal.write", input.method)
            assertEquals(JsonPrimitive("AP8N"), input.params!!.jsonObject["data"])
            assertEquals(JsonPrimitive("ws"), input.params!!.jsonObject["workspaceId"])
            server.reply(input, """{"ok":true}""")
            write.get(2, TimeUnit.SECONDS)
            val size = source.resize("term", 100, 30)
            val resize = server.request()
            assertEquals("terminal.resize", resize.method)
            assertEquals(JsonPrimitive(100), resize.params!!.jsonObject["cols"])
            assertEquals(JsonPrimitive(30), resize.params!!.jsonObject["rows"])
            server.reply(resize, """{"ok":true}""")
            size.get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun subscriptionFiltersWorkspaceAndCarriesExactCursors() {
        val transports = LinkedBlockingQueue<TerminalTransport>()
        RpcSession({
            TerminalTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val received = LinkedBlockingQueue<TerminalData>()
            val ready = LinkedBlockingQueue<Boolean>()
            val source = RpcTerminalSession(rpc, "ws")
            val lease = source.subscribe({
                received.add(it)
            }, {}, { ready.add(true) }, { throw it })
            val subscribe = server.request()
            assertEquals("events.subscribe", subscribe.method)
            server.reply(subscribe, """{"subscriptionId":"s"}""")
            ready.poll(2, TimeUnit.SECONDS)
            server.event("other")
            server.event("ws")
            val data = requireNotNull(received.poll(2, TimeUnit.SECONDS))
            assertEquals("18446744073709551614", data.startOffset)
            assertEquals("18446744073709551615", data.endOffset)
            assertNull(received.poll())
            lease.close()
            assertEquals("events.unsubscribe", server.request().method)
        }
    }
}

private class TerminalTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage) = sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request = requireNotNull(sent.poll(2, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, payload: String) = listener.onMessage(
        JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload).jsonObject)
    )
    fun event(workspace: String) = listener.onMessage(
        JsonRpcMessage.Notification(
            "events.event",
            WireJson.parseToJsonElement(
                """{"subscriptionId":"s","event":{"type":"terminal:data","workspaceId":"$workspace",
                "id":"event","timestamp":"now","actor":{"type":"system"},
                "data":{"terminalId":"term","chunk":"YQ==","daemonBootId":"boot",
                "startOffset":"18446744073709551614","endOffset":"18446744073709551615"}}}"""
            ).jsonObject
        )
    )
}
