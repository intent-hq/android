package dev.intent.app.agents

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionTransport
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentSessionTest {
    @Test
    fun collectionAdmissionListAndCleanupKeepWorkspaceScope() {
        val transports = LinkedBlockingQueue<AgentTestTransport>()
        RpcSession(
            { AgentTestTransport(it).also(transports::add) },
            JsonObject(emptyMap())
        ).use { rpc ->
            val source = RpcAgentSession(rpc, "workspace")
            val pushes = LinkedBlockingQueue<SubscriptionPush<Agent>>()
            val admitted = LinkedBlockingQueue<Boolean>()
            val lease = source.subscribe(pushes::add, { admitted.add(true) }, { throw it })
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val subscribe = server.request()
            assertEquals("agent.subscribe", subscribe.method)
            assertEquals(JsonPrimitive("workspace"), subscribe.params!!.jsonObject["workspaceId"])
            server.reply(subscribe, """{"subscriptionId":"lease"}""")
            assertEquals(true, admitted.poll(2, TimeUnit.SECONDS))
            server.listener.onMessage(
                JsonRpcMessage.Notification(
                    "subscription.push",
                    json(
                        """{"subscriptionId":"lease","seq":0,"kind":"snapshot","snapshot":[]}"""
                    )
                )
            )
            assertEquals(emptyList<Agent>(), pushes.poll(2, TimeUnit.SECONDS)!!.snapshot)
            val read = source.list()
            val request = server.request()
            assertEquals("agent.list", request.method)
            assertEquals(JsonPrimitive("workspace"), request.params!!.jsonObject["workspaceId"])
            server.reply(request, """{"agents":[{"id":"agent","name":"Test"}]}""")
            assertEquals("agent", read.get(2, TimeUnit.SECONDS).single().id)
            lease.close()
            val cleanup = server.request()
            assertEquals("events.unsubscribe", cleanup.method)
            assertEquals(JsonPrimitive("workspace"), cleanup.params!!.jsonObject["workspaceId"])
            assertEquals(JsonPrimitive("lease"), cleanup.params!!.jsonObject["subscriptionId"])
        }
    }
}

private class AgentTestTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage): Boolean =
        sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request =
        requireNotNull(sent.poll(2, TimeUnit.SECONDS)) { "No request" }
    fun reply(request: JsonRpcMessage.Request, payload: String) =
        listener.onMessage(JsonRpcMessage.Success(request.id, json(payload)))
}

private fun json(text: String) = WireJson.parseToJsonElement(text).jsonObject
