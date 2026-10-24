package dev.intent.app.connection

import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.pairing.CredentialBytes
import dev.intent.core.protocol.pairing.CredentialStore
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.SessionTransport
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionControllerTest {
    @Test
    fun `deep link requires confirmation before persistence or connection`() {
        val repository = repository()
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.prepareLink(
                "intent://pair?host=desktop.local&port=443&token=secret&fp=${"AB".repeat(32)}"
            )
            await { controller.state.value.pending != null }
            assertTrue(repository.list().isEmpty())
            assertTrue(transports.isEmpty())
            controller.cancelPairing()
            await { controller.state.value.pending == null }
            assertTrue(repository.list().isEmpty())
            controller.prepareLink(
                "intent://pair?host=desktop.local&port=443&token=secret&fp=${"AB".repeat(32)}"
            )
            await { controller.state.value.pending != null }
            controller.confirmPairing()
            val transport = transports.poll(2, TimeUnit.SECONDS)!!
            admit(transport, "person-a")
            await { controller.state.value.session != null }
            assertEquals("Connected", controller.state.value.status)
            assertEquals(1, repository.list().size)
            assertFalse(controller.state.value.cacheScope!!.contains("secret"))
        }
    }

    @Test
    fun `encrypted records round trip and selected connection bootstraps after restart`() {
        val repository = repository()
        val saved = repository.add(payload())
        repository.select(saved.id)
        assertEquals(saved, repository.list().single())
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.restore()
            val server = transports.poll(2, TimeUnit.SECONDS)!!
            val hello = server.requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("client.hello", hello.method)
            assertEquals(
                JsonPrimitive("android-${saved.id}"),
                (hello.params as JsonObject)["clientId"]
            )
            server.reply(hello, JsonObject(emptyMap()))
            val principal = server.requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("principal.me", principal.method)
            assertNull(controller.state.value.session)
            server.reply(principal, JsonObject(mapOf("id" to JsonPrimitive("person-a"))))
            await { controller.state.value.session != null }
            assertEquals(
                connectionCacheScope(payload(), "person-a"),
                controller.state.value.cacheScope
            )
            controller.disconnect()
            await { controller.state.value.activeId == null }
            assertNull(repository.activeId())
            assertEquals(1, repository.list().size)
        }
    }

    @Test
    fun `switch rejects delayed principal reply from previous connection`() {
        val repository = repository()
        val first = repository.add(payload())
        val second = repository.add(payload().copy(hosts = listOf("second.local")))
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.select(first.id)
            val old = transports.poll(2, TimeUnit.SECONDS)!!
            old.reply(old.requests.poll(2, TimeUnit.SECONDS)!!, JsonObject(emptyMap()))
            val delayed = old.requests.poll(2, TimeUnit.SECONDS)!!
            controller.select(second.id)
            val next = transports.poll(2, TimeUnit.SECONDS)!!
            old.reply(delayed, JsonObject(mapOf("id" to JsonPrimitive("wrong-person"))))
            admit(next, "right-person")
            await { controller.state.value.session != null }
            assertEquals(second.id, controller.state.value.activeId)
            assertEquals(
                connectionCacheScope(second.payload, "right-person"),
                controller.state.value.cacheScope
            )
            controller.forget(second.id)
            await { controller.state.value.activeId == null }
            assertEquals(listOf(first), repository.list())
        }
    }

    @Test
    fun `invalid and tunnel only links never connect and errors redact tokens`() {
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository(), transports).use { controller ->
            controller.prepareLink("intent://pair?port=443&token=SECRET&tc=tunnel")
            await { controller.state.value.error != null }
            assertNull(controller.state.value.pending)
            assertFalse(controller.state.value.error!!.contains("SECRET"))
            assertTrue(transports.isEmpty())
        }
    }

    @Test
    fun `missing fingerprint needs a separate probe and trust action`() {
        val repository = repository()
        var probes = 0
        ConnectionController(repository, probe = { _, _ ->
            probes++
            "AB".repeat(32)
        }).use { controller ->
            controller.prepareLink("intent://pair?host=desktop.local&port=443&token=secret")
            await { controller.state.value.pending != null }
            assertEquals(0, probes)
            assertNull(controller.state.value.pending!!.certFingerprint)
            controller.probeCertificate()
            await { controller.state.value.pending?.certFingerprint != null }
            assertEquals(1, probes)
            assertTrue(repository.list().isEmpty())
            assertNull(controller.state.value.session)
            controller.cancelPairing()
            await { controller.state.value.pending == null }
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun `missing principal fails closed without exposing a session or cache`() {
        val repository = repository()
        val saved = repository.add(payload())
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.select(saved.id)
            val server = transports.poll(2, TimeUnit.SECONDS)!!
            server.reply(server.requests.poll(2, TimeUnit.SECONDS)!!, JsonObject(emptyMap()))
            server.reply(server.requests.poll(2, TimeUnit.SECONDS)!!, JsonObject(emptyMap()))
            await { controller.state.value.error != null }
            assertNull(controller.state.value.session)
            assertNull(controller.state.value.cacheScope)
        }
    }

    @Test
    fun `cache identity isolates principals and certificates but survives token rotation`() {
        val original = connectionCacheScope(payload(), "person-a")
        assertEquals(original, connectionCacheScope(payload().copy(token = "rotated"), "person-a"))
        assertNotEquals(original, connectionCacheScope(payload(), "person-b"))
        assertNotEquals(
            original,
            connectionCacheScope(payload().copy(certFingerprint = "CD".repeat(32)), "person-a")
        )
        assertEquals(
            "wss://[::1]:443/ws",
            connectionEndpoint(payload().copy(hosts = listOf("::1")))
        )
    }

    private fun payload() = PairingPayload(listOf("desktop.local"), 443, "secret", "AB".repeat(32))

    private fun controller(
        repository: ConnectionRepository,
        transports: LinkedBlockingQueue<FakeTransport>
    ) = ConnectionController(repository, { saved, callback ->
        createConnectionSession(saved, callback) { listener ->
            FakeTransport(listener).also(transports::add)
        }
    })

    private fun repository(): SavedConnections {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val records = mutableMapOf<String, Bytes>()
        var active: String? = null
        return SavedConnections(
            { records.filterValues { it.value != null }.keys.toList() },
            { id -> CredentialStore(records.getOrPut(id) { Bytes() }) { key } },
            { active },
            { active = it }
        )
    }

    private fun admit(server: FakeTransport, principal: String) {
        server.reply(server.requests.poll(2, TimeUnit.SECONDS)!!, JsonObject(emptyMap()))
        val request = server.requests.poll(2, TimeUnit.SECONDS)!!
        assertEquals("principal.me", request.method)
        server.reply(request, JsonObject(mapOf("id" to JsonPrimitive(principal))))
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Timed out waiting for connection state", condition())
    }

    private class Bytes : CredentialBytes {
        var value: ByteArray? = null
        override fun read(): ByteArray? = value
        override fun write(value: ByteArray) {
            this.value = value
        }
        override fun delete() {
            value = null
        }
    }

    private class FakeTransport(val listener: WssTransport.Listener) : SessionTransport {
        val requests = LinkedBlockingQueue<JsonRpcMessage.Request>()
        override fun connect() = listener.onOpen()
        override fun send(message: JsonRpcMessage): Boolean {
            requests.add(message as JsonRpcMessage.Request)
            return true
        }
        fun reply(request: JsonRpcMessage.Request, result: JsonObject) =
            listener.onMessage(JsonRpcMessage.Success(request.id, result))
        override fun close() = Unit
    }
}
