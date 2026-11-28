package dev.intentapp.android.workspaces

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

    fun recover() {}
}

class RpcWorkspaceSession(private val session: RpcSession) : WorkspaceSession {
    override fun recover() {
        session.recoverNow()
    }

    /** Reads every page; the daemon returns 100 rows per page, oldest first. */
    override fun list(): CompletableFuture<List<Workspace>> = page(null, emptyList(), emptySet())

    private fun page(
        token: String?,
        rows: List<Workspace>,
        seen: Set<String>
    ): CompletableFuture<List<Workspace>> = session.request(
        "workspace.list",
        buildJsonObject {
            put("includeArchived", true)
            token?.let { put("nextToken", it) }
        },
        WorkspaceListResult.serializer()
    ).thenCompose { result ->
        val next = result.nextToken
        val all = rows + result.workspaces
        if (next == null || next in seen || result.workspaces.isEmpty()) {
            CompletableFuture.completedFuture(all)
        } else {
            page(next, all, seen + next)
        }
    }

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
