package dev.intent.app.connection

import dev.intent.app.conversation.ConversationSendStore
import dev.intent.app.conversation.RpcConversationCommands
import dev.intent.app.conversation.SendStatus
import dev.intent.app.workspaces.RpcWorkspaceSession
import dev.intent.app.workspaces.WorkspaceStore
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
import org.junit.Assert.assertSame
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
            connectionEndpoint("::1", payload().port)
        )
    }

    @Test
    fun `transport reconnect retains conversation owner and pending send without replay`() {
        val repository = repository()
        val saved = repository.add(payload())
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.select(saved.id)
            val first = transports.poll(2, TimeUnit.SECONDS)!!
            admit(first, "person-a")
            await { controller.state.value.session != null }
            val admitted = controller.state.value
            val session = requireNotNull(admitted.session)
            ConversationSendStore(RpcConversationCommands(session, "workspace", "agent"), "agent")
                .use { sends ->
                    sends.start()
                    serveConversation(first)
                    await { !sends.state.value.checking }
                    sends.send("Hello")
                    val send = first.requests.poll(2, TimeUnit.SECONDS)!!
                    assertEquals("agent.sendMessage", send.method)
                    val id = (send.params as JsonObject)["messageId"] as JsonPrimitive
                    first.listener.onClosed(1006, "lost")
                    await { sends.state.value.attempts.single().status == SendStatus.UNCONFIRMED }
                    // These are the exact keys used by ConnectedContent and remember(session).
                    assertSame(session, controller.state.value.session)
                    assertEquals(admitted.cacheScope, controller.state.value.cacheScope)
                    assertEquals(id.content, sends.state.value.attempts.single().id)
                    val next = transports.poll(2, TimeUnit.SECONDS)!!
                    admit(next, "person-a")
                    serveConversation(next, id.content)
                    await { sends.state.value.attempts.isEmpty() }
                    assertSame(session, controller.state.value.session)
                    assertEquals(admitted.cacheScope, controller.state.value.cacheScope)
                    assertEquals(1, first.methods.count { it == "agent.sendMessage" })
                    assertFalse(next.methods.contains("agent.sendMessage"))
                }
        }
    }

    @Test
    fun `changed principal closes retained session before restoring subscriptions`() {
        val repository = repository()
        val saved = repository.add(payload())
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.select(saved.id)
            val first = transports.poll(2, TimeUnit.SECONDS)!!
            admit(first, "person-a")
            await { controller.state.value.session != null }
            val session = requireNotNull(controller.state.value.session)
            ConversationSendStore(RpcConversationCommands(session, "workspace", "agent"), "agent")
                .use { sends ->
                    sends.start()
                    serveConversation(first)
                    first.listener.onClosed(1006, "lost")
                    val next = transports.poll(2, TimeUnit.SECONDS)!!
                    admit(next, "person-b")
                    await { controller.state.value.error != null }
                    assertNull(controller.state.value.session)
                    assertNull(controller.state.value.cacheScope)
                    assertEquals(listOf("client.hello", "principal.me"), next.methods.toList())
                }
        }
    }

    @Test
    fun `route persistence rejects a stale snapshot or a different active connection`() {
        val repository = repository()
        val first = repository.add(payload())
        val second = repository.add(payload().copy(hosts = listOf("other.local")))
        repository.select(first.id)
        val updated = first.payload.copy(hosts = listOf("desktop.local", "10.0.0.2"))
        assertTrue(repository.refresh(first, updated))
        assertFalse(repository.refresh(first, first.payload))
        repository.select(second.id)
        assertFalse(repository.refresh(first.copy(payload = updated), first.payload))
        assertEquals(updated, repository.list().first { it.id == first.id }.payload)
        assertEquals(2, repository.list().size)
    }

    @Test
    fun `admitted session refreshes saved routes without replacing the session`() {
        val repository = repository()
        val saved = repository.add(payload())
        val transports = LinkedBlockingQueue<FakeTransport>()
        val status = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"localIps":["10.0.0.2"],"tcAddress":"tunnel"}"""
        ) as JsonObject
        controller(repository, transports, status).use { controller ->
            controller.select(saved.id)
            val server = transports.poll(2, TimeUnit.SECONDS)!!
            assertFalse(server.methods.contains("system.status"))
            admit(server, "person-a")
            await { repository.list().single().payload.hosts.size == 2 }
            assertEquals(saved.id, repository.activeId())
            assertEquals("tunnel", repository.list().single().payload.tcAddress)
            assertEquals("secret", repository.list().single().payload.token)
            assertEquals(1, repository.list().size)
            assertTrue(controller.state.value.session != null)
        }
    }

    private fun serveConversation(server: FakeTransport, appId: String? = null) {
        repeat(4) {
            val request = server.requests.poll(2, TimeUnit.SECONDS)!!
            val text = when (request.method) {
                "events.subscribe" -> """{"subscriptionId":"events"}"""
                "agent.getQueue" -> """{"success":true,"queue":[]}"""
                "agent.get" -> """{"agent":{"id":"agent","name":"Agent"}}"""
                "agent.getConversation" -> {
                    val rows = if (appId == null) {
                        "[]"
                    } else {
                        """[{"id":"canonical","role":"user","appMessageId":"$appId"}]"""
                    }
                    """{"agentId":"agent","messages":$rows}"""
                }
                else -> error("Unexpected request: ${request.method}")
            }
            server.reply(
                request,
                kotlinx.serialization.json.Json.parseToJsonElement(text) as JsonObject
            )
        }
    }

    private fun payload() = PairingPayload(listOf("desktop.local"), 443, "secret", "AB".repeat(32))

    @Test
    fun `switch to same principal safely rejects stale screen subscription`() =
        switchWithStaleScreen("person-a")

    @Test
    fun `switch to different principal safely rejects stale screen subscription`() =
        switchWithStaleScreen("person-b")

    private fun switchWithStaleScreen(principal: String) {
        val repository = repository()
        val first = repository.add(payload())
        val second = repository.add(payload().copy(hosts = listOf("second.local")))
        val transports = LinkedBlockingQueue<FakeTransport>()
        controller(repository, transports).use { controller ->
            controller.select(first.id)
            val old = transports.poll(2, TimeUnit.SECONDS)!!
            admit(old, "person-a")
            await { controller.state.value.session != null }
            await { old.methods.contains("system.status") }
            val snapshot = controller.state.value
            val staleSession = requireNotNull(snapshot.session)
            controller.select(second.id)
            val next = transports.poll(2, TimeUnit.SECONDS)!!
            // The next endpoint has not admitted us yet (also covers an unreachable host).
            assertNull(controller.state.value.session)
            assertNull(controller.state.value.cacheScope)
            // Compose may start an effect with its previous collected state after select.
            WorkspaceStore(RpcWorkspaceSession(staleSession)).use { staleStore ->
                staleStore.start()
                assertFalse(staleStore.state.value.isLoading)
                assertTrue(staleStore.state.value.error != null)
                admit(next, principal)
                await { controller.state.value.session != null }
                assertNotEquals(staleSession, controller.state.value.session)
                assertEquals(second.id, controller.state.value.activeId)
                assertEquals(
                    connectionCacheScope(second.payload, principal),
                    controller.state.value.cacheScope
                )
                assertEquals(
                    listOf("client.hello", "principal.me", "system.status"),
                    old.methods.toList()
                )
                assertTrue(staleStore.state.value.workspaces.isEmpty())
            }
        }
    }

    private fun controller(
        repository: ConnectionRepository,
        transports: LinkedBlockingQueue<FakeTransport>,
        hostStatus: JsonObject = JsonObject(emptyMap())
    ) = ConnectionController(repository, { saved, callback ->
        createConnectionSession(saved, callback) { listener ->
            FakeTransport(listener, hostStatus).also(transports::add)
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

    private class FakeTransport(
        val listener: WssTransport.Listener,
        val hostStatus: JsonObject = JsonObject(emptyMap())
    ) : SessionTransport {
        val requests = LinkedBlockingQueue<JsonRpcMessage.Request>()
        val methods = LinkedBlockingQueue<String>()
        override fun connect() = listener.onOpen()
        override fun send(message: JsonRpcMessage): Boolean {
            methods.add((message as JsonRpcMessage.Request).method)
            if (message.method == "system.status") {
                reply(message, hostStatus)
            } else {
                requests.add(message)
            }
            return true
        }
        fun reply(request: JsonRpcMessage.Request, result: JsonObject) =
            listener.onMessage(JsonRpcMessage.Success(request.id, result))
        override fun close() = Unit
    }
}
