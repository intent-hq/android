package dev.intent.app.terminals

import dev.intent.core.protocol.models.EventNotification
import dev.intent.core.protocol.models.TerminalAck
import dev.intent.core.protocol.models.TerminalBuffer
import dev.intent.core.protocol.models.TerminalData
import dev.intent.core.protocol.models.TerminalListResult
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

interface TerminalSession {
    fun list(): CompletableFuture<TerminalListResult>
    fun buffer(id: String): CompletableFuture<TerminalBuffer>
    fun write(id: String, bytes: ByteArray): CompletableFuture<TerminalAck>
    fun resize(id: String, columns: Int, rows: Int): CompletableFuture<TerminalAck>
    fun subscribe(
        onData: (TerminalData) -> Unit,
        onExit: (String) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable
}

class RpcTerminalSession(private val rpc: RpcSession, private val workspaceId: String) :
    TerminalSession {
    private fun params(block: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("workspaceId", workspaceId)
        block()
    }

    override fun list() = rpc.request("terminal.list", params(), TerminalListResult.serializer())
    override fun buffer(id: String) = rpc.request(
        "terminal.getBuffer",
        params { put("terminalId", id) },
        TerminalBuffer.serializer()
    )
    override fun write(id: String, bytes: ByteArray) = rpc.request(
        "terminal.write",
        params {
            put("terminalId", id)
            put("data", Base64.getEncoder().encodeToString(bytes))
        },
        TerminalAck.serializer()
    )
    override fun resize(id: String, columns: Int, rows: Int) = rpc.request(
        "terminal.resize",
        params {
            put("terminalId", id)
            put("cols", columns)
            put("rows", rows)
        },
        TerminalAck.serializer()
    )
    override fun subscribe(
        onData: (TerminalData) -> Unit,
        onExit: (String) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable = rpc.subscribe(
        SubscriptionSpec(
            "events.subscribe",
            params {
                putJsonArray("eventTypes") {
                    add(kotlinx.serialization.json.JsonPrimitive("terminal:data"))
                    add(kotlinx.serialization.json.JsonPrimitive("terminal:exit"))
                }
            },
            notificationMethod = "events.event"
        ),
        onMessage = { payload ->
            runCatching {
                val event = WireJson.decodeFromJsonElement(
                    EventNotification.serializer(),
                    payload
                ).event
                if (event.workspaceId == workspaceId) {
                    when (event.type) {
                        "terminal:data" -> onData(
                            WireJson.decodeFromJsonElement(TerminalData.serializer(), event.data)
                        )
                        "terminal:exit" -> event.data["terminalId"]?.jsonPrimitive?.content?.let(
                            onExit
                        )
                    }
                }
            }.onFailure(onError)
        },
        onSubscribed = { onSubscribed() },
        onError = onError
    )
}
