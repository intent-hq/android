package dev.intent.app.connection

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.SessionTransport
import dev.intent.tailcat.TunnelClient
import dev.intent.tailcat.TunnelClientFactory
import dev.intent.tailcat.TunnelForwarder
import dev.intent.tailcat.UnavailableTunnelClientFactory
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostRacingTransportTest {
    @Test
    fun `first successful direct host wins and late losers cannot deliver events`() {
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        race(events, sockets).use { race ->
            race.connect()
            val first = sockets.next()
            val second = sockets.next()
            second.listener.onOpen()
            assertEquals("open", events.queue.next())
            assertTrue(first.closed.await(2, TimeUnit.SECONDS))
            assertEquals(second.endpoint, race.endpoint)
            first.listener.onOpen()
            first.listener.onFailure(IOException("late"))
            first.listener.onMessage(JsonRpcMessage.Notification("ignored"))
            val message = JsonRpcMessage.Notification("winner")
            second.listener.onMessage(message)
            assertEquals(message, events.queue.next())
            assertNull(events.queue.poll())
            assertTrue(race.send(message))
            assertEquals(message, second.sent.next())
        }
    }

    @Test
    fun `failure of one host leaves other candidates alive and all failures notify once`() {
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        race(events, sockets).use { race ->
            race.connect()
            val first = sockets.next()
            val second = sockets.next()
            first.listener.onFailure(IOException("first"))
            assertTrue(first.closed.await(2, TimeUnit.SECONDS))
            assertNull(events.queue.poll())
            second.listener.onFailure(IOException("second"))
            assertTrue(events.queue.next() is IOException)
            first.listener.onFailure(IOException("duplicate"))
            assertNull(events.queue.poll())
        }
    }

    @Test
    fun `tunnel winner owns the loopback route and closes forwarder before client`() {
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        val tunnel = FakeTunnel()
        race(events, sockets, tunnel).use { race ->
            race.connect()
            val candidates = List(3) { sockets.next() }
            val winner = candidates.single { "127.0.0.1" in it.endpoint }
            winner.listener.onOpen()
            assertEquals("open", events.queue.next())
            assertEquals("wss://127.0.0.1:54321/ws", race.endpoint)
            assertEquals("tc-capability", tunnel.address.next())
            assertEquals(443, tunnel.port.next())
            candidates.filter { it !== winner }.forEach {
                assertTrue(it.closed.await(2, TimeUnit.SECONDS))
            }
        }
        assertEquals("forwarder", tunnel.closed.next())
        assertEquals("client", tunnel.closed.next())
    }

    @Test
    fun `slow tunnel cannot delay a direct winner and late resources are closed`() {
        val release = CountDownLatch(1)
        val tunnel = FakeTunnel(beforeCreate = { release.await() })
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        try {
            race(events, sockets, tunnel).use { race ->
                race.connect()
                val first = sockets.next()
                sockets.next()
                assertEquals("tc-capability", tunnel.address.next())
                first.listener.onOpen()
                assertEquals("open", events.queue.next())
                release.countDown()
                assertEquals("client", tunnel.closed.next())
                assertNull(tunnel.port.poll())
                assertNull(sockets.poll())
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `close during forward drains a late forwarder and never opens its socket`() {
        val release = CountDownLatch(1)
        val tunnel = FakeTunnel(beforeForward = { release.await() })
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        val race = race(events, sockets, tunnel)
        try {
            race.connect()
            sockets.next()
            sockets.next()
            assertEquals(443, tunnel.port.next())
            race.close()
            assertEquals("client", tunnel.closed.next())
            release.countDown()
            assertEquals("forwarder", tunnel.closed.next())
            assertNull(sockets.poll())
            assertNull(events.queue.poll())
            assertFalse(race.send(JsonRpcMessage.Notification("closed")))
        } finally {
            release.countDown()
            race.close()
        }
    }

    @Test
    fun `unavailable tunnel falls back to direct and tunnel-only fails cleanly`() {
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        race(events, sockets).use { race ->
            race.connect()
            sockets.next().listener.onOpen()
            assertEquals("open", events.queue.next())
        }
        HostRacingTransport(
            payload().copy(hosts = emptyList()),
            events,
            UnavailableTunnelClientFactory
        ).use {
            it.connect()
            assertTrue(events.queue.next() is IOException)
        }
    }

    @Test
    fun `timeout cancels silent candidates`() {
        val sockets = LinkedBlockingQueue<FakeSocket>()
        val events = Events()
        race(events, sockets, timeoutMillis = 100).use { race ->
            race.connect()
            val first = sockets.next()
            val second = sockets.next()
            assertTrue(events.queue.next() is IOException)
            assertTrue(first.closed.await(2, TimeUnit.SECONDS))
            assertTrue(second.closed.await(2, TimeUnit.SECONDS))
        }
    }

    private fun race(
        events: Events,
        sockets: LinkedBlockingQueue<FakeSocket>,
        tunnel: TunnelClientFactory = UnavailableTunnelClientFactory,
        timeoutMillis: Long = 5000
    ) = HostRacingTransport(
        payload(),
        events,
        tunnel,
        { endpoint, listener -> FakeSocket(endpoint, listener).also { sockets.add(it) } },
        timeoutMillis
    )

    private fun payload() = PairingPayload(
        listOf("first.local", "second.local"),
        443,
        "bearer-secret",
        "ab".repeat(32),
        "tc-capability"
    )

    private class FakeSocket(val endpoint: String, val listener: WssTransport.Listener) :
        SessionTransport {
        val closed = CountDownLatch(1)
        val sent = LinkedBlockingQueue<JsonRpcMessage>()
        override fun connect() = Unit
        override fun send(message: JsonRpcMessage): Boolean = sent.add(message)
        override fun close() = closed.countDown()
    }

    private class Events : WssTransport.Listener {
        val queue = LinkedBlockingQueue<Any>()
        override fun onOpen() {
            queue.add("open")
        }
        override fun onMessage(message: JsonRpcMessage) {
            queue.add(message)
        }
        override fun onFailure(error: Throwable) {
            queue.add(error)
        }
    }
}

internal class FakeTunnel(
    private val localPort: Int = 54321,
    private val beforeCreate: () -> Unit = {},
    private val beforeForward: () -> Unit = {}
) : TunnelClientFactory {
    override val available = true
    val address = LinkedBlockingQueue<String>()
    val port = LinkedBlockingQueue<Int>()
    val closed = LinkedBlockingQueue<String>()
    override fun createClient(token: String): TunnelClient {
        address.add(token)
        beforeCreate()
        return object : TunnelClient {
            override fun ping(timeoutMs: Int) = Unit
            override fun forward(remotePort: Int): TunnelForwarder {
                port.add(remotePort)
                beforeForward()
                return object : TunnelForwarder {
                    override val localPort = this@FakeTunnel.localPort
                    override fun close() {
                        closed.add("forwarder")
                    }
                }
            }
            override fun close() {
                closed.add("client")
            }
        }
    }
}

internal fun <T> LinkedBlockingQueue<T>.next(): T = checkNotNull(poll(3, TimeUnit.SECONDS))
