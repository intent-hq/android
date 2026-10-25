package dev.intent.app.smoke

import dev.intent.app.connection.ConnectionRepository
import dev.intent.app.connection.SavedConnection
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.SessionTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json

/** In-process protocol fixture. No credentials or requests leave the test process. */
internal class SmokeServer(private val listener: WssTransport.Listener) : SessionTransport {
    val requests = CopyOnWriteArrayList<JsonRpcMessage.Request>()
    override fun connect() = listener.onOpen()
    override fun close() = Unit
    override fun send(message: JsonRpcMessage): Boolean {
        val request = message as JsonRpcMessage.Request
        requests.add(request)
        val result = when (request.method) {
            "client.hello", "system.status", "workspace.unsubscribe", "events.unsubscribe" -> "{}"
            "principal.me" -> """{"id":"smoke-person"}"""
            "workspace.subscribe", "events.subscribe" -> """{"subscriptionId":"smoke"}"""
            "workspace.list" ->
                """{"workspaces":[{"id":"workspace","title":"Android smoke workspace"}]}"""
            "agent.sendMessage" -> """{"success":true,"queued":false}"""
            else -> error("Unexpected smoke request: ${request.method}")
        }
        listener.onMessage(JsonRpcMessage.Success(request.id, Json.parseToJsonElement(result)))
        return true
    }
}

internal class MemoryConnections : ConnectionRepository {
    private val records = CopyOnWriteArrayList<SavedConnection>()
    private var active: String? = null
    override fun list(): List<SavedConnection> = records.toList()
    override fun activeId(): String? = active
    override fun select(id: String?) {
        active = id
    }
    override fun add(payload: PairingPayload): SavedConnection =
        SavedConnection("smoke-connection", payload).also { records.add(it) }
    override fun remove(id: String) {
        records.removeAll { it.id == id }
    }
}
