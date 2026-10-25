package dev.intent.core.protocol.attachments

import dev.intent.core.protocol.session.RpcSession
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

fun interface AttachmentRpc {
    fun request(method: String, params: JsonObject): CompletableFuture<JsonObject>
}

class RpcAttachmentClient(private val session: RpcSession) : AttachmentRpc {
    override fun request(method: String, params: JsonObject) =
        session.request(method, params, timeoutMillis = TRANSFER_TIMEOUT_MILLIS).thenApply {
            it.jsonObject
        }
}

internal class AttachmentCalls(val rpc: AttachmentRpc, val workspaceId: String) {
    private val json = Json { ignoreUnknownKeys = true }

    fun params(block: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("workspaceId", workspaceId)
        block()
    }

    fun <T> call(method: String, params: JsonObject, serializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(serializer, rpc.request(method, params).join())
}

private const val TRANSFER_TIMEOUT_MILLIS = 300_000L
internal const val MAX_ATTACHMENT_BYTES = 1024L * 1024 * 1024
internal const val SINGLE_SHOT_BYTES = 25 * 1024 * 1024
internal const val CHUNK_BYTES = 16 * 1024 * 1024
