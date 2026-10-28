package dev.intent.app.status

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
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
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSourceTest {
    @Test fun prActionsUseWorkspaceIdentityAndFreshCheck() {
        val transports = LinkedBlockingQueue<WatchTransport>()
        RpcSession({
            WatchTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val source = RpcWatchSource(rpc, "ws")
            for (action in PrMonitorAction.entries) {
                val result = source.prAction("monitor", action)
                val request = server.request()
                assertEquals("prMonitor.${action.method}", request.method)
                val expected = if (action == PrMonitorAction.FLUSH) {
                    """{"workspaceId":"ws","monitorId":"monitor","check":true}"""
                } else {
                    """{"workspaceId":"ws","monitorId":"monitor"}"""
                }
                assertEquals(WireJson.parseToJsonElement(expected), request.params)
                server.reply(request, """{"ok":true,"flushed":false}""")
                result.get(2, TimeUnit.SECONDS)
            }
            val failed = source.prAction("monitor", PrMonitorAction.CANCEL)
            server.reply(server.request(), """{"ok":false}""")
            assertTrue(runCatching { failed.get(2, TimeUnit.SECONDS) }.isFailure)
        }
    }

    @Test fun acknowledgedFeedUsesCapabilityAndBoundedSummaryReads() {
        val transports = LinkedBlockingQueue<WatchTransport>()
        RpcSession({
            WatchTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            val source = RpcWatchSource(rpc, "ws")
            val admitted = LinkedBlockingQueue<Boolean>()
            val events = LinkedBlockingQueue<JsonObject>()
            val lease = source.subscribe(WatchFamily.SCRIPT, events::add, {
                admitted.add(true)
            }, { throw it })
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(
                server.request(),
                """{"server":{"protocolVersion":"99",
"capabilities":{"scriptMonitors":2,
"scriptActiveLists":1}}}"""
            )
            val subscribe = server.request()
            assertEquals("events.subscribe", subscribe.method)
            assertEquals(JsonPrimitive("ws"), subscribe.params!!.jsonObject["workspaceId"])
            server.reply(subscribe, """{"subscriptionId":"lease"}""")
            assertEquals(true, admitted.poll(2, TimeUnit.SECONDS))
            assertTrue(source.scriptsSupported)
            val read = source.list(WatchFamily.SCRIPT, "cursor")
            val request = server.request()
            assertEquals("scriptMonitor.list", request.method)
            assertEquals(JsonPrimitive("summary"), request.params!!.jsonObject["view"])
            assertEquals(JsonPrimitive("active"), request.params!!.jsonObject["state"])
            assertEquals(JsonPrimitive(100), request.params!!.jsonObject["limit"])
            assertEquals(JsonPrimitive("cursor"), request.params!!.jsonObject["cursor"])
            server.reply(
                request,
                """{"view":"summary",
"monitors":[],
"nextCursor":"more"}"""
            )
            assertEquals("more", read.get(2, TimeUnit.SECONDS).nextCursor)
            val detail = source.detail("selected")
            val get = server.request()
            assertEquals("scriptMonitor.get", get.method)
            assertEquals(JsonPrimitive("selected"), get.params!!.jsonObject["monitorId"])
            server.reply(
                get,
                """{"monitorId":"wrong",
"workspaceId":"ws",
"state":"active"}"""
            )
            assertTrue(runCatching { detail.get(2, TimeUnit.SECONDS) }.isFailure)
            lease.close()
            assertEquals("events.unsubscribe", server.request().method)
        }
    }

    @Test fun unknownCapabilityIsNotSupportAndPrFeedIsGlobal() {
        val transports = LinkedBlockingQueue<WatchTransport>()
        RpcSession({
            WatchTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            val source = RpcWatchSource(rpc, "ws")
            val admitted = LinkedBlockingQueue<Boolean>()
            source.subscribe(WatchFamily.PR, {}, { admitted.add(true) }, { throw it }).use {
                rpc.connect()
                val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
                server.reply(server.request(), "{}")
                val subscribe = server.request()
                assertFalse("workspaceId" in subscribe.params!!.jsonObject)
                server.reply(subscribe, """{"subscriptionId":"lease"}""")
                assertEquals(true, admitted.poll(2, TimeUnit.SECONDS))
                assertFalse(source.scriptsSupported)
                val read = source.list(WatchFamily.PR, null)
                val request = server.request()
                assertEquals("prMonitor.list", request.method)
                assertEquals(JsonPrimitive("ws"), request.params!!.jsonObject["workspaceId"])
                server.reply(request, """{"monitors":[]}""")
                assertTrue(read.get(2, TimeUnit.SECONDS).rows.isEmpty())
            }
        }
    }
}

private class WatchTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage): Boolean =
        sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request = requireNotNull(sent.poll(2, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, payload: String) = listener.onMessage(
        JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload))
    )
}
