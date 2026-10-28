package dev.intent.app.conversation

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.ChatEntity
import dev.intent.core.protocol.models.EntityDelta
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ConversationActivity {
    STREAM_START,
    STREAM_END,
    RUNNING,
    IDLE;

    internal fun streaming(chatLive: Boolean): Boolean? = when (this) {
        STREAM_START -> true.takeUnless { chatLive }
        STREAM_END -> false.takeUnless { chatLive }
        RUNNING -> true
        IDLE -> false
    }
}

/** Only assistant terminal blocks end a turn; queued/user echoes do not. */
internal fun EntityDelta<ChatEntity>.streaming(current: Boolean, agentId: String): Boolean {
    val assistant = (added + updated).filter { it.agentId == agentId && it.role == "assistant" }
    return when {
        assistant.any { it.streamingComplete == true } -> false
        assistant.isNotEmpty() -> true
        else -> current
    }
}

internal fun Agent.conversationRunning(): Boolean = turnInFlight ||
    isStreaming ||
    isResponding ||
    isProcessing ||
    status.lowercase() in setOf("responding", "streaming", "processing", "running")

internal fun subscribeConversationActivity(
    session: RpcSession,
    workspaceId: String,
    agentId: String,
    onActivity: (ConversationActivity) -> Unit
): Closeable = session.subscribe(
    SubscriptionSpec(
        "events.subscribe",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put(
                "eventTypes",
                buildJsonArray {
                    listOf("agent:stream:*", "agent:status-changed", "agent:idle")
                        .forEach { add(JsonPrimitive(it)) }
                }
            )
        },
        notificationMethod = "events.event"
    ),
    onMessage = { payload ->
        conversationActivity(payload, workspaceId, agentId)?.let(onActivity)
    }
)

internal fun conversationActivity(
    payload: JsonObject,
    workspaceId: String,
    agentId: String
): ConversationActivity? {
    val event = payload["event"] as? JsonObject ?: payload
    val data = event["data"] as? JsonObject ?: return null
    val workspace = data.string("workspaceId") ?: event.string("workspaceId")
    return if (data.string("agentId") != agentId ||
        (workspace != null && workspace != workspaceId)
    ) {
        null
    } else {
        activityForEvent(event.string("type"), data.string("status") ?: data.string("newStatus"))
    }
}

private fun activityForEvent(type: String?, status: String?): ConversationActivity? = when (type) {
    "agent:stream:start", "agent:stream:activity" -> ConversationActivity.STREAM_START
    "agent:stream:end" -> ConversationActivity.STREAM_END
    "agent:idle" -> ConversationActivity.IDLE
    "agent:status-changed" -> when (status?.lowercase()) {
        "responding", "streaming", "processing", "running" -> ConversationActivity.RUNNING
        "idle", "completed", "complete", "failed", "error" -> ConversationActivity.IDLE
        else -> null
    }
    else -> null
}
