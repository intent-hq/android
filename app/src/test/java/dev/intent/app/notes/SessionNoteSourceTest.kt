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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SessionNoteSourceTest {
    @Test
    fun specLoadsWithBareTaskArrayAndFullContent() = loadNote(
        "spec",
        "Specification\n".repeat(10_000),
        """[{"lineNumber":2,"text":"Work","status":"todo","taskNoteId":"task-a"}]""",
        1
    )

    @Test
    fun taskNoteLoadsWithEmptyTaskArray() = loadNote("task-a", "Task body", "[]", 0)

    private fun loadNote(noteId: String, content: String, tasks: String, taskCount: Int) {
        val transports = LinkedBlockingQueue<NoteTestTransport>()
        RpcSession(
            { NoteTestTransport(it).also(transports::add) },
            JsonObject(emptyMap())
        ).use { rpc ->
            NoteStore(SessionNoteSource(rpc)).use { store ->
                store.open("workspace", noteId)
                rpc.connect()
                val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
                server.reply(server.request(), "{}")
                val subscribe = server.request()
                assertEquals("events.subscribe", subscribe.method)
                assertEquals(
                    JsonPrimitive("workspace"),
                    subscribe.params!!.jsonObject["workspaceId"]
                )
                server.reply(subscribe, """{"subscriptionId":"lease"}""")
                val noteRequest = server.request()
                val tasksRequest = server.request()
                assertEquals("note.get", noteRequest.method)
                assertEquals("note.listTasks", tasksRequest.method)
                val expectedParams = buildJsonObject {
                    put("workspaceId", "workspace")
                    put("noteId", noteId)
                }
                assertEquals(expectedParams, noteRequest.params)
                assertEquals(expectedParams, tasksRequest.params)
                val note = buildJsonObject {
                    put("id", noteId)
                    put("title", "Note")
                    put("content", content)
                }
                server.reply(noteRequest, buildJsonObject { put("note", note) }.toString())
                // intentd's note.listTasks router returns the array itself, including [].
                server.reply(tasksRequest, tasks)
                val barrier = rpc.request("test.barrier")
                server.reply(server.request(), "{}")
                barrier.get(2, TimeUnit.SECONDS)
                assertNull(store.state.error)
                assertFalse(store.state.loading)
                assertEquals(content, store.state.note?.content)
                assertEquals(taskCount, store.state.tasks.size)
            }
        }
    }
}

private class NoteTestTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage) = sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request(): JsonRpcMessage.Request =
        requireNotNull(sent.poll(2, TimeUnit.SECONDS)) { "No request" }
    fun reply(request: JsonRpcMessage.Request, payload: String) = listener.onMessage(
        JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload))
    )
}
