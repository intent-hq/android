package dev.intent.app.scripts

import dev.intent.core.protocol.models.WorkspaceScriptActionResult
import dev.intent.core.protocol.models.WorkspaceScriptList
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface WorkspaceScriptSession {
    fun list(): CompletableFuture<List<WorkspaceScriptResource>>
    fun status(id: String): CompletableFuture<WorkspaceScriptRuntime>
    fun setRunning(id: String, running: Boolean): CompletableFuture<WorkspaceScriptActionResult>
    fun subscribe(changed: () -> Unit, ready: () -> Unit, failed: (Throwable) -> Unit): Closeable
}

class RpcWorkspaceScriptSession(private val session: RpcSession, private val workspaceId: String) :
    WorkspaceScriptSession {
    override fun list(): CompletableFuture<List<WorkspaceScriptResource>> = session.request(
        "script.list",
        params(),
        WorkspaceScriptList.serializer()
    ).thenApply { it.scripts }

    override fun status(id: String): CompletableFuture<WorkspaceScriptRuntime> = session.request(
        "script.status",
        params(id),
        WorkspaceScriptRuntime.serializer()
    )

    override fun setRunning(id: String, running: Boolean) = session.request(
        if (running) "script.start" else "script.stop",
        params(id),
        WorkspaceScriptActionResult.serializer()
    )

    override fun subscribe(changed: () -> Unit, ready: () -> Unit, failed: (Throwable) -> Unit) =
        session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put("eventTypes", JsonArray(listOf("script:state", "script:changed").map(::JsonPrimitive)))
                },
                notificationMethod = "events.event"
            ),
            onMessage = { payload ->
                val event = payload["event"] as? JsonObject
                if (event?.get("workspaceId") == JsonPrimitive(workspaceId)) changed()
            },
            onSubscribed = { ready() },
            onError = failed
        )

    private fun params(id: String? = null) = buildJsonObject {
        put("workspaceId", workspaceId)
        id?.let { put("scriptId", it) }
    }
}
