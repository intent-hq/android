package dev.intent.app.git

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.GitFileStatus
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

class GitSessionTest {
    @Test
    fun readsKeepWorkspacePathAndStagedScope() {
        val transports = LinkedBlockingQueue<GitTestTransport>()
        RpcSession({
            GitTestTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val source = RpcGitSession(rpc, "ws")
            val diff = source.diff(GitFileStatus("src/a.kt", "M", true))
            val request = server.request()
            assertEquals("git.diffs", request.method)
            assertEquals(JsonPrimitive("ws"), request.params!!.jsonObject["workspaceId"])
            assertEquals(JsonPrimitive("src/a.kt"), request.params!!.jsonObject["path"])
            assertEquals(JsonPrimitive(true), request.params!!.jsonObject["staged"])
            server.reply(request, "[]")
            assertEquals(emptyList<Any>(), diff.get(2, TimeUnit.SECONDS))
            val history = source.commits("cursor")
            val page = server.request()
            assertEquals("file-tracking.loadCommits", page.method)
            assertEquals(JsonPrimitive("cursor"), page.params!!.jsonObject["nextToken"])
            server.reply(page, """{"commits":[]}""")
            assertEquals(emptyList<Any>(), history.get(2, TimeUnit.SECONDS).items)
        }
    }

    @Test
    fun eventsIgnoreOtherWorkspacesAndIncludeRootChangesAndCloseLease() {
        val transports = LinkedBlockingQueue<GitTestTransport>()
        RpcSession({
            GitTestTransport(it).also(transports::add)
        }, JsonObject(emptyMap())).use { rpc ->
            val refreshed = LinkedBlockingQueue<Boolean>()
            val lease = RpcGitSession(rpc, "ws").watch({ refreshed.add(true) }, { throw it })
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val subscribe = server.request()
            assertEquals("events.subscribe", subscribe.method)
            server.reply(subscribe, """{"subscriptionId":"git"}""")
            assertEquals(true, refreshed.poll(2, TimeUnit.SECONDS))
            server.event("""{"workspaceId":"other"}""", 0)
            server.event("""{"workspaceId":"ws","gitRootId":"secondary"}""", 1)
            server.event("""{"workspaceId":"ws"}""", 2)
            assertEquals(true, refreshed.poll(2, TimeUnit.SECONDS))
            assertEquals(true, refreshed.poll(2, TimeUnit.SECONDS))
            lease.close()
            assertEquals("events.unsubscribe", server.request().method)
            assertEquals(0, refreshed.size)
        }
    }
}

internal class GitTestTransport(val listener: WssTransport.Listener) : SessionTransport {
    private val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun send(message: JsonRpcMessage) = sent.add(message as JsonRpcMessage.Request)
    override fun close() = Unit
    fun request() = requireNotNull(sent.poll(2, TimeUnit.SECONDS))
    fun reply(request: JsonRpcMessage.Request, payload: String) =
        listener.onMessage(JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(payload)))
    fun event(data: String, sequence: Int) = listener.onMessage(
        JsonRpcMessage.Notification(
            "events.event",
            WireJson.parseToJsonElement(
                """{"subscriptionId":"git","seq":$sequence,"event":{"type":"changes:git-status","data":$data}}"""
            )
        )
    )
}
