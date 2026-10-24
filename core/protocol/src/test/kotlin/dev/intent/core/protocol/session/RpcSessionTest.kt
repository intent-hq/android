package dev.intent.core.protocol.session

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.RpcError
import dev.intent.core.protocol.WssTransport
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcSessionTest {
    @Test
    fun `requests correlate out of order and retain typed errors`() = fixture { session, server ->
        val first = session.request("note.get")
        val second = session.request("workspace.list")
        val a = server.request()
        val b = server.request()
        server.reply(b, "{\"value\":2}")
        server.reply(a, "{\"value\":1}")
        assertEquals(JsonPrimitive(1), first.get(2, TimeUnit.SECONDS).jsonObject["value"])
        assertEquals(JsonPrimitive(2), second.get(2, TimeUnit.SECONDS).jsonObject["value"])
        val failure = session.request("missing")
        val request = server.request()
        val error = RpcError(-32602, "Missing", json("{\"code\":\"not-found\"}"))
        server.listener.onMessage(JsonRpcMessage.Failure(request.id, error))
        assertEquals(error, (failed(failure) as RpcException).error)
    }

    @Test
    fun `timeout and cancellation reject late replies`() = fixture { session, server ->
        val request = session.request("slow", timeoutMillis = 20)
        val frame = server.request()
        assertTrue(failed(request) is TimeoutException)
        server.reply(frame)
        val cancelled = session.request("cancel")
        val cancelFrame = server.request()
        cancelled.cancel(false)
        server.reply(cancelFrame)
        assertTrue(cancelled.isCancelled)
    }

    @Test
    fun `reconnect retires old replies and restores subscriptions after hello`() {
        val servers = LinkedBlockingQueue<FakeTransport>()
        RpcSession({
            FakeTransport(it).also(servers::add)
        }, json("{}"), reconnectDelay = { 1 }).use { session ->
            val events = LinkedBlockingQueue<JsonObject>()
            val admissions = LinkedBlockingQueue<String>()
            val lease = session.subscribe(spec(), events::add, admissions::add)
            session.connect()
            val first = servers.poll(2, TimeUnit.SECONDS)!!
            first.reply(first.request())
            first.reply(first.request(), "{\"subscriptionId\":\"old\"}")
            first.push("old", 0)
            assertEquals("old", lease.ready.get(2, TimeUnit.SECONDS))
            assertEquals("old", admissions.poll(2, TimeUnit.SECONDS))
            assertTrue(events.poll(2, TimeUnit.SECONDS) != null)
            val pending = session.request("mutation")
            first.request()
            first.listener.onFailure(IOException("lost heartbeat"))
            assertTrue(failed(pending) is SessionDisconnectedException)
            val second = servers.poll(2, TimeUnit.SECONDS)!!
            val hello = second.request()
            assertEquals("client.hello", hello.method)
            second.reply(hello)
            val restore = second.request()
            assertEquals("note.subscribe", restore.method)
            second.reply(restore, "{\"subscriptionId\":\"new\"}")
            assertEquals("new", admissions.poll(2, TimeUnit.SECONDS))
            first.push("old", 1)
            second.push("old", 1)
            second.push("new", 0)
            assertEquals(JsonPrimitive("new"), events.poll(2, TimeUnit.SECONDS)!!["subscriptionId"])
            lease.close()
            assertEquals("events.unsubscribe", second.request().method)
            second.push("new", 1)
            assertEquals(null, events.poll(50, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `generic events fan out and loss markers stay private`() = fixture { session, server ->
        val events = LinkedBlockingQueue<JsonObject>()
        repeat(2) { index ->
            val lease = session.subscribe(
                SubscriptionSpec(
                    "events.subscribe",
                    json("{\"eventTypes\":[\"note:*\"]}"),
                    "events.event"
                ),
                events::add
            )
            server.reply(server.request(), "{\"subscriptionId\":\"s$index\"}")
            lease.ready.get(2, TimeUnit.SECONDS)
            server.listener.onMessage(
                JsonRpcMessage.Notification(
                    "events.event",
                    json("{\"subscriptionId\":\"s$index\",\"event\":{\"id\":\"same\"}}")
                )
            )
        }
        repeat(2) { assertTrue(events.poll(2, TimeUnit.SECONDS) != null) }
        server.listener.onMessage(
            JsonRpcMessage.Notification("events.lost", json("{\"subscriptionId\":\"s0\"}"))
        )
        assertEquals(null, events.poll(50, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `sequence gaps resubscribe and duplicate pushes are ignored`() = fixture {
            session,
            server
        ->
        val events = LinkedBlockingQueue<JsonObject>()
        session.subscribe(spec(), events::add)
        server.reply(server.request(), "{\"subscriptionId\":\"s\"}")
        server.push("s", 0)
        assertTrue(events.poll(2, TimeUnit.SECONDS) != null)
        server.push("s", 0)
        server.push("s", 2)
        assertEquals("events.unsubscribe", server.request().method)
        assertEquals("note.subscribe", server.request().method)
        assertEquals(null, events.poll(50, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `failed hello does not restore subscriptions or admit requests`() {
        val servers = LinkedBlockingQueue<FakeTransport>()
        RpcSession({
            FakeTransport(it).also(servers::add)
        }, json("{}"), reconnectDelay = { 1000 }).use { session ->
            session.subscribe(spec(), {})
            session.connect()
            val server = servers.poll(2, TimeUnit.SECONDS)!!
            val hello = server.request()
            server.listener.onMessage(
                JsonRpcMessage.Failure(hello.id, RpcError(-32012, "Daemon not ready"))
            )
            assertTrue(failed(session.request("workspace.list")) is SessionDisconnectedException)
            assertEquals(null, server.sent.poll(50, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `closing before subscription ack removes the late server lease`() = fixture {
            session,
            server
        ->
        val events = LinkedBlockingQueue<JsonObject>()
        val lease = session.subscribe(spec(), events::add)
        val admission = server.request()
        lease.close()
        server.reply(admission, "{\"subscriptionId\":\"late\"}")
        val unsubscribe = server.request()
        assertEquals("events.unsubscribe", unsubscribe.method)
        assertEquals(JsonPrimitive("w"), (unsubscribe.params as JsonObject)["workspaceId"])
        server.push("late", 0)
        assertEquals(null, events.poll(50, TimeUnit.MILLISECONDS))
        assertTrue(lease.ready.isCancelled)
    }

    @Test
    fun `close fails outstanding calls and rejects new calls`() = fixture { session, server ->
        val pending = session.request("wait")
        server.request()
        session.close()
        assertTrue(failed(pending) is SessionDisconnectedException)
        assertTrue(failed(session.request("after-close")) is SessionDisconnectedException)
    }

    private fun fixture(test: (RpcSession, FakeTransport) -> Unit) {
        val servers = LinkedBlockingQueue<FakeTransport>()
        val ready = CompletableFuture<Unit>()
        RpcSession({ FakeTransport(it).also(servers::add) }, json("{}"), onState = {
            if (it ==
                SessionState.READY
            ) {
                ready.complete(Unit)
            }
        }).use { session ->
            session.connect()
            val server = servers.poll(2, TimeUnit.SECONDS)!!
            server.reply(server.request())
            ready.get(2, TimeUnit.SECONDS)
            test(session, server)
        }
    }

    private fun failed(future: CompletableFuture<*>): Throwable? =
        assertThrows(ExecutionException::class.java) { future.get(2, TimeUnit.SECONDS) }.cause

    private fun spec() = SubscriptionSpec("note.subscribe", json("{\"workspaceId\":\"w\"}"))

    private class FakeTransport(val listener: WssTransport.Listener) : SessionTransport {
        val sent = LinkedBlockingQueue<JsonRpcMessage.Request>()
        override fun connect() = listener.onOpen()
        override fun send(message: JsonRpcMessage): Boolean =
            sent.add(message as JsonRpcMessage.Request)
        override fun close() = Unit
        fun request(): JsonRpcMessage.Request =
            sent.poll(2, TimeUnit.SECONDS) ?: error("No request")
        fun reply(request: JsonRpcMessage.Request, payload: String = "{}") =
            listener.onMessage(JsonRpcMessage.Success(request.id, json(payload)))
        fun push(id: String, sequence: Int) = listener.onMessage(
            JsonRpcMessage.Notification(
                "subscription.push",
                json(
                    "{\"subscriptionId\":\"$id\",\"seq\":$sequence," +
                        "\"kind\":\"${if (sequence == 0) "snapshot" else "delta"}\",\"snapshot\":[]}"
                )
            )
        )
    }

    companion object {
        private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject
    }
}
