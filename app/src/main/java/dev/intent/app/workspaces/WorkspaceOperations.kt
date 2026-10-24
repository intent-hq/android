package dev.intent.app.workspaces

import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Blocking operations are called only on the feature's worker executor. Mutations never retry. */
interface WorkspaceOperations {
    fun request(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject
    fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable
}

class RpcWorkspaceOperations(private val session: RpcSession) : WorkspaceOperations {
    override fun request(method: String, params: JsonObject): JsonObject = session.request(
        method,
        params,
        OPERATION_TIMEOUT
    ).get(OPERATION_TIMEOUT + RESPONSE_MARGIN, TimeUnit.MILLISECONDS) as? JsonObject
        ?: error("Invalid host response")

    @Suppress("TooGenericExceptionCaught") // Close the subscription on any admission failure.
    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable {
        val lease = session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put("eventTypes", JsonArray(listOf(JsonPrimitive("workspace:transfer:*"))))
                },
                notificationMethod = "events.event"
            ),
            onMessage = receive
        )
        try {
            lease.ready.get(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            lease.close()
            throw error
        }
        return lease
    }
}

internal fun JsonObject.string(key: String): String =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: error("Missing $key")
internal fun JsonObject.number(key: String): Long =
    (get(key) as? JsonPrimitive)?.longOrNull ?: error("Missing $key")
internal fun JsonObject.obj(key: String): JsonObject =
    get(key) as? JsonObject ?: error("Missing $key")
internal fun JsonObject.rows(key: String): List<JsonObject> =
    (get(key) as? JsonArray)?.map { it as? JsonObject ?: error("Invalid $key") }
        ?: error("Missing $key")
private const val OPERATION_TIMEOUT = 120_000L

private const val RESPONSE_MARGIN = 1000
