package dev.intent.app.conversation

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
import org.junit.Test

class AttachmentSendSessionTest {
    @Test
    fun attachmentReferencesAreTopLevelSendParameters() {
        val transports = LinkedBlockingQueue<AttachmentTransport>()
        RpcSession({
            AttachmentTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val commands = RpcConversationCommands(rpc, "ws", "agent")
            val image = WireJson.parseToJsonElement(
                """{"type":"image","attachmentId":"photo"}"""
            ).jsonObject
            val result = commands.sendAttachments("message", "", null, listOf(image), emptyList())
            val request = server.request()
            assertEquals("agent.sendMessage", request.method)
            assertEquals(
                WireJson.parseToJsonElement(
                    """{"workspaceId":"ws","agentId":"agent","messageId":"message","userAppMessageId":"message",
                "content":"","imageBlocks":[{"type":"image","attachmentId":"photo"}]}"""
                ),
                request.params
            )
            server.reply(request, """{"success":true,"queued":false,"messageId":"message"}""")
            assertEquals("message", result.get(2, TimeUnit.SECONDS).messageId)
        }
    }
}

private class AttachmentTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage) = sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request = requireNotNull(sent.poll(2, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, payload: String) = listener.onMessage(
        JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload).jsonObject)
    )
}
