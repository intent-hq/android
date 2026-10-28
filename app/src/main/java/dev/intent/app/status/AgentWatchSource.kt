package dev.intent.app.status

import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface AgentWatchSource {
    fun read(): CompletableFuture<AgentWatches>
    fun subscribe(
        changed: (String) -> Unit,
        ready: () -> Unit,
        error: (Throwable) -> Unit
    ): Closeable
}

class RpcAgentWatchSource(
    private val session: RpcSession,
    private val workspaceId: String,
    private val agentId: String
) : AgentWatchSource {
    override fun read(): CompletableFuture<AgentWatches> = session.request(
        "agent.getSubscriptions",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("agentId", agentId)
        }
    ).thenApply { parseAgentWatches(it as? JsonObject ?: error("Invalid agent subscriptions")) }

    override fun subscribe(
        changed: (String) -> Unit,
        ready: () -> Unit,
        error: (Throwable) -> Unit
    ): Closeable = session.subscribe(
        SubscriptionSpec(
            "events.subscribe",
            buildJsonObject {
                put("workspaceId", workspaceId)
                put("eventTypes", JsonArray(AGENT_WATCH_EVENTS.map(::JsonPrimitive)))
            },
            notificationMethod = "events.event"
        ),
        onMessage = { payload ->
            val event = payload.objectValue("event") ?: payload
            val data = event.objectValue("data")
            val workspaces = listOfNotNull(data?.text("workspaceId"), event.text("workspaceId"))
            val id = data?.text("agentId")
            if (id != null && workspaces.all { it == workspaceId }) {
                changed(id)
            }
        },
        onSubscribed = { ready() },
        onError = error
    )
}

private val AGENT_WATCH_EVENTS = listOf(
    "agent:subscribed", "agent:unsubscribed", "agent:subscriptions-changed",
    "agent:subscriptions-restored", "agent:woken-by-subscription", "agent:status-changed",
    "agent:idle", "agent:completed", "agent:failed", "agent:deleted",
    "agent:updated", "agent:stream:end"
)
