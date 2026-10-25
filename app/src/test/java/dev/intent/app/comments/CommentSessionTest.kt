package dev.intent.app.comments

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.CommentThreadSummary
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

class CommentSessionTest {
    @Test
    fun collectionAdmissionListAndCleanupKeepWorkspaceScope() {
        val transports = LinkedBlockingQueue<CommentTestTransport>()
        RpcSession(
            { CommentTestTransport(it).also(transports::add) },
            JsonObject(emptyMap())
        ).use { rpc ->
            val source = RpcCommentSession(rpc, "workspace", "note")
            val pushes = LinkedBlockingQueue<SubscriptionPush<CommentThreadSummary>>()
            val admitted = LinkedBlockingQueue<Boolean>()
            val lease = source.subscribe(pushes::add, { admitted.add(true) }, { throw it })
            rpc.connect()
            val server = requireNotNull(transports.poll(2, TimeUnit.SECONDS))
            server.reply(server.request(), "{}")
            val subscribe = server.request()
            assertEquals("comment.subscribe", subscribe.method)
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
            assertEquals(
                emptyList<CommentThreadSummary>(),
                pushes.poll(2, TimeUnit.SECONDS)!!.snapshot
            )
            val read = source.list()
            val request = server.request()
            assertEquals("comment.list", request.method)
            assertEquals(JsonPrimitive("workspace"), request.params!!.jsonObject["workspaceId"])
            assertEquals(JsonPrimitive("note"), request.params!!.jsonObject["noteId"])
            assertEquals(JsonPrimitive(true), request.params!!.jsonObject["includeComments"])
            server.reply(request, """{"threads":[],"totalThreads":0,"totalComments":0}""")
            assertEquals(emptyList<CommentThreadSummary>(), read.get(2, TimeUnit.SECONDS))
            source.reply("thread", "Hello")
            val reply = server.request()
            assertEquals("comment.respond", reply.method)
            assertEquals(JsonPrimitive("workspace"), reply.params!!.jsonObject["workspaceId"])
            assertEquals(JsonPrimitive("note"), reply.params!!.jsonObject["noteId"])
            assertEquals(JsonPrimitive("thread"), reply.params!!.jsonObject["threadId"])
            assertEquals(JsonPrimitive("user"), reply.params!!.jsonObject["authorType"])
            assertEquals(JsonPrimitive("Hello"), reply.params!!.jsonObject["comment"])
            lease.close()
            val cleanup = server.request()
            assertEquals("comment.unsubscribe", cleanup.method)
            assertEquals(JsonPrimitive("workspace"), cleanup.params!!.jsonObject["workspaceId"])
            assertEquals(JsonPrimitive("lease"), cleanup.params!!.jsonObject["subscriptionId"])
        }
    }
}

private class CommentTestTransport(val listener: WssTransport.Listener) : SessionTransport {
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
