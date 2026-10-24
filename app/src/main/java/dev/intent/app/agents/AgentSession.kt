package dev.intent.app.agents

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.AgentListResult
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface AgentSession {
    fun list(): CompletableFuture<List<Agent>>
    fun subscribe(
        onPush: (SubscriptionPush<Agent>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable
}

class RpcAgentSession(private val session: RpcSession, private val workspaceId: String) :
    AgentSession {
    override fun list(): CompletableFuture<List<Agent>> = session.request(
        "agent.list",
        buildJsonObject { put("workspaceId", workspaceId) },
        AgentListResult.serializer()
    ).thenApply { it.agents }

    override fun subscribe(
        onPush: (SubscriptionPush<Agent>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable = session.subscribe(
        SubscriptionSpec(
            "agent.subscribe",
            buildJsonObject { put("workspaceId", workspaceId) },
            unsubscribeMethod = "events.unsubscribe"
        ),
        onMessage = { payload ->
            runCatching {
                WireJson.decodeFromJsonElement(
                    SubscriptionPush.serializer(Agent.serializer()),
                    payload
                )
            }.onSuccess(onPush).onFailure(onError)
        },
        onSubscribed = { onSubscribed() },
        onError = onError
    )
}
