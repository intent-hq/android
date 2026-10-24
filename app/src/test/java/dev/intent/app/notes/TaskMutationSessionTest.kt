package dev.intent.app.notes

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
import org.junit.Test

class TaskMutationSessionTest {
    @Test
    fun `status commands carry workspace identity and concurrency guards`() {
        val transports = LinkedBlockingQueue<TaskTransport>()
        RpcSession({ TaskTransport(it).also(transports::add) }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val source = SessionNoteSource(rpc)
            val line = source.updateLine("ws", "spec", 2, "done", "Work")
            val request = server.request()
            assertEquals("task.update", request.method)
            assertEquals(
                WireJson.parseToJsonElement(
                    """{"workspaceId":"ws","noteId":"spec","line":2,"status":"done","expected":"Work"}"""
                ),
                request.params
            )
            server.reply(
                request,
                """{"ok":true,"noteId":"spec","lineNumber":2,"previousText":"Work","newText":"Work","status":"done"}"""
            )
            assertEquals("done", line.get(2, TimeUnit.SECONDS).status)
            val status = source.updateStatus("ws", "task", "complete", 7)
            val update = server.request()
            assertEquals("task.updateNoteStatus", update.method)
            assertEquals(JsonPrimitive(7), update.params!!.jsonObject["expectedVersion"])
            server.reply(
                update,
                """{"ok":true,"noteId":"task","status":"complete",
                "note":{"id":"task","title":"Work",
                "metadata":{"task":{"status":"complete"}},"rev":8}}"""
            )
            assertEquals(8L, status.get(2, TimeUnit.SECONDS).note.rev)
        }
    }
}

private class TaskTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage) = sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request = requireNotNull(sent.poll(2, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, payload: String) = listener.onMessage(
        JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload).jsonObject)
    )
}
