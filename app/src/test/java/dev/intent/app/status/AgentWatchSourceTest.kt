package dev.intent.app.status

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentWatchSourceTest {
    @Test fun readsCapturedSubscriptionsAndReceivesScopedChangesAfterAdmission() {
        val transports = LinkedBlockingQueue<AgentWatchTransport>()
        RpcSession({
            AgentWatchTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            val source = RpcAgentWatchSource(rpc, "port-create", "parent")
            val ready = LinkedBlockingQueue<Boolean>()
            val changed = LinkedBlockingQueue<String>()
            source.subscribe(changed::add, { ready.add(true) }, { throw it }).use {
                rpc.connect()
                val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
                server.reply(server.request(), "{}")
                val subscription = server.request()
                assertEquals("events.subscribe", subscription.method)
                server.reply(subscription, """{"subscriptionId":"watch-feed"}""")
                assertEquals(true, ready.poll(2, TimeUnit.SECONDS))
                val read = source.read()
                val request = server.request()
                assertEquals("agent.getSubscriptions", request.method)
                assertEquals(
                    WireJson.parseToJsonElement(
                        """{"workspaceId":"port-create","agentId":"parent"}"""
                    ),
                    request.params
                )
                server.reply(
                    request,
                    requireNotNull(javaClass.getResource("/agent-watches-live.json")).readText()
                )
                assertEquals(3, read.get(2, TimeUnit.SECONDS).rows.size)
                server.event("other")
                assertNull(changed.poll(100, TimeUnit.MILLISECONDS))
                server.event("port-create")
                assertEquals("parent", changed.poll(2, TimeUnit.SECONDS))
            }
        }
    }
}

private class AgentWatchTransport(private val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage): Boolean =
        sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request = requireNotNull(sent.poll(2, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, payload: String) = listener.onMessage(
        JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload))
    )
    fun event(workspace: String) = listener.onMessage(
        JsonRpcMessage.Notification(
            "events.event",
            WireJson.parseToJsonElement(
                """{"subscriptionId":"watch-feed",
                "event":{"type":"agent:subscriptions-changed","workspaceId":"$workspace",
                "data":{"agentId":"parent"}}}"""
            ).jsonObject
        )
    )
}
