package dev.intent.app.workspaces

import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.models.Workspace
import dev.intent.core.protocol.models.WorkspaceListResult
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface WorkspaceSession {
    fun list(): CompletableFuture<List<Workspace>>
    fun subscribe(
        onPush: (SubscriptionPush<Workspace>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable
}

class RpcWorkspaceSession(private val session: RpcSession) : WorkspaceSession {
    override fun list(): CompletableFuture<List<Workspace>> = session.request(
        "workspace.list",
        buildJsonObject { put("includeArchived", true) },
        WorkspaceListResult.serializer()
    ).thenApply { it.workspaces }

    override fun subscribe(
        onPush: (SubscriptionPush<Workspace>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable = session.subscribe(
        SubscriptionSpec(
            "workspace.subscribe",
            JsonObject(emptyMap()),
            unsubscribeMethod = "workspace.unsubscribe"
        ),
        onMessage = { payload ->
            runCatching {
                WireJson.decodeFromJsonElement(
                    SubscriptionPush.serializer(Workspace.serializer()),
                    payload
                )
            }.onSuccess(onPush).onFailure(onError)
        },
        onSubscribed = { onSubscribed() },
        onError = onError
    )
}
