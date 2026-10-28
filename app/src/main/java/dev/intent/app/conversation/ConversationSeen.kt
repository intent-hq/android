package dev.intent.app.conversation

import dev.intent.core.protocol.session.RpcSession
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Entry preserves unread state; turn completion, sends, and departure advance the marker. */
class ConversationSeen(
    private val mark: (String) -> CompletableFuture<String?>,
    private val schedule: (() -> Unit) -> Closeable
) {
    private var candidate: String? = null
    private var active = false
    private var streaming = false
    private var pending: Closeable? = null
    private val sent = mutableSetOf<String>()
    private var confirmed: String? = null

    @Synchronized
    fun observe(state: ConversationState, visible: Boolean, knownMarker: String? = null) {
        if (knownMarker != null) confirmed = knownMarker
        val finished = active && visible && streaming && !state.isStreaming
        if (visible && !state.isLoading && state.error == null) {
            candidate =
                state.messages.lastOrNull { it.isStreaming != true && it.id.isNotEmpty() }?.id
        }
        if (active && !visible) flush()
        active = visible
        streaming = state.isStreaming
        if (finished) sentMessage()
    }

    @Synchronized
    fun sentMessage() {
        if (!active) return
        pending?.close()
        pending = schedule { flush() }
    }

    @Synchronized
    fun leave() {
        active = false
        flush()
    }

    @Synchronized
    private fun flush() {
        pending?.close()
        pending = null
        val id = candidate ?: return
        if (id == confirmed || !sent.add(id)) return
        mark(id).whenComplete { marker, error ->
            synchronized(this) {
                if (error == null && marker != null) confirmed = marker else sent.remove(id)
            }
        }
    }
}

internal fun markConversationSeen(
    session: RpcSession,
    workspaceId: String,
    agentId: String,
    messageId: String
): CompletableFuture<String?> = session.request(
    "agent.markSeen",
    buildJsonObject {
        put("workspaceId", workspaceId)
        put("agentId", agentId)
        put("messageId", messageId)
    },
    JsonObject.serializer()
).thenApply { result ->
    result["lastSeenMessageId"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { result["success"]?.jsonPrimitive?.booleanOrNull == true }
}
