package dev.intent.app.conversation

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSessionTest {
    @Test
    fun coordinatorSnapshotUsesSupportedDeliveryAndLoadsMixedBlocks() {
        val transports = LinkedBlockingQueue<ConversationTestTransport>()
        RpcSession(
            { ConversationTestTransport(it).also(transports::add) },
            JsonObject(emptyMap())
        ).use { rpc ->
            val store =
                ConversationStore(RpcConversationSession(rpc, "workspace", "agent"), "agent")
            store.start()
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val requests = listOf(server.request(), server.request())
            val subscribe = requests.single { it.method == "chat.subscribe" }
            server.reply(
                requests.single {
                    it.method == "events.subscribe"
                },
                """{"subscriptionId":"events"}"""
            )
            assertEquals("chat.subscribe", subscribe.method)
            val params = subscribe.params!!.jsonObject
            assertEquals(JsonPrimitive("snapshot"), params["historyDelivery"])
            assertEquals(JsonPrimitive("slim"), params["projection"])
            assertEquals(JsonPrimitive(20), params["limit"])
            assertEquals(JsonPrimitive("workspace"), params["workspaceId"])
            server.reply(subscribe, """{"subscriptionId":"lease"}""")
            val fixture = requireNotNull(
                javaClass.getResource("/coordinator-snapshot.json")
            ).readText()
            server.listener.onMessage(
                JsonRpcMessage.Notification("subscription.push", json(fixture))
            )
            // A request/reply on the same executor fences the preceding snapshot callback.
            val barrier = rpc.request("test.barrier")
            server.reply(server.request(), "{}")
            barrier.get(2, TimeUnit.SECONDS)
            assertTrue(store.state.value.isStreaming)
            assertFalse(store.state.value.isLoading)
            assertNull(store.state.value.error)
            assertEquals("older", store.state.value.nextToken)
            assertEquals(2, store.state.value.messages.size)
            assertEquals(6, store.state.value.messages.last().contentBlocks.size)
            fun event(type: String) {
                server.listener.onMessage(
                    JsonRpcMessage.Notification(
                        "events.event",
                        json(
                            """{"subscriptionId":"events","event":{"type":"$type",
                            "data":{"agentId":"agent","workspaceId":"workspace","newStatus":"processing"}}}"""
                        )
                    )
                )
                val fence = rpc.request("test.barrier")
                server.reply(server.request(), "{}")
                fence.get(2, TimeUnit.SECONDS)
            }
            event("agent:stream:end")
            assertTrue(store.state.value.isStreaming)
            event("agent:idle")
            assertFalse(store.state.value.isStreaming)
            event("agent:status-changed")
            assertTrue(store.state.value.isStreaming)
            store.close()
        }
    }
}

private class ConversationTestTransport(val listener: WssTransport.Listener) : SessionTransport {
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
