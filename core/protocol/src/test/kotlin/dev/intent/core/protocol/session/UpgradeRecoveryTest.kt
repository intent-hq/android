package dev.intent.core.protocol.session

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WebSocketUpgradeException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeRecoveryTest {
    @Test
    fun `authentication and permission rejections close without retrying`() {
        listOf(401, 403).forEach { code ->
            val states = LinkedBlockingQueue<SessionState>()
            val retries = AtomicInteger()
            RpcSession(
                { listener ->
                    rejectingTransport { listener.onFailure(WebSocketUpgradeException(code)) }
                },
                JsonObject(emptyMap()),
                reconnectDelay = { retries.incrementAndGet().toLong() },
                onState = states::add
            ).use { session ->
                session.connect()
                assertEquals(SessionState.CONNECTING, states.poll(2, TimeUnit.SECONDS))
                assertEquals(SessionState.CLOSED, states.poll(2, TimeUnit.SECONDS))
                assertEquals(code, session.rejectedStatusCode)
                assertEquals(0, retries.get())
                assertTrue(session.request("workspace.list").isCompletedExceptionally)
            }
        }
    }

    @Test
    fun `temporary server rejection still retries`() {
        val attempts = LinkedBlockingQueue<Unit>()
        RpcSession(
            { listener ->
                rejectingTransport {
                    attempts.add(Unit)
                    listener.onFailure(WebSocketUpgradeException(503))
                }
            },
            JsonObject(emptyMap()),
            reconnectDelay = { 1 }
        ).use { session ->
            session.connect()
            repeat(2) { assertEquals(Unit, attempts.poll(2, TimeUnit.SECONDS)) }
            assertEquals(null, session.rejectedStatusCode)
        }
    }

    private fun rejectingTransport(connect: () -> Unit) = object : SessionTransport {
        override fun connect() = connect.invoke()
        override fun send(message: JsonRpcMessage) = false
        override fun close() = Unit
    }
}
