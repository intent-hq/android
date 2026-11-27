package dev.intentapp.android.scripts

import dev.intent.core.protocol.models.WorkspaceScriptActionResult
import dev.intent.core.protocol.models.WorkspaceScriptList
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface WorkspaceScriptSession {
    fun list(): CompletableFuture<List<WorkspaceScriptResource>>
    fun status(id: String): CompletableFuture<WorkspaceScriptRuntime>
    fun setRunning(id: String, running: Boolean): CompletableFuture<WorkspaceScriptActionResult>
    fun create(
        name: String,
        command: String,
        mode: String
    ): CompletableFuture<WorkspaceScriptResource>
    fun remove(id: String): CompletableFuture<WorkspaceScriptActionResult>
    fun output(id: String): CompletableFuture<String>

    /** Manifest scripts that are not saved yet; failures yield an empty list, like iOS. */
    fun discover(): CompletableFuture<List<WorkspaceScriptRow>>
    fun subscribe(changed: () -> Unit, ready: () -> Unit, failed: (Throwable) -> Unit): Closeable
}

@Suppress("TooManyFunctions") // One function per script RPC plus paging helpers.
class RpcWorkspaceScriptSession(private val session: RpcSession, private val workspaceId: String) :
    WorkspaceScriptSession {
    override fun list(): CompletableFuture<List<WorkspaceScriptResource>> =
        page(null, emptyList(), 0)

    private fun page(
        cursor: String?,
        rows: List<WorkspaceScriptResource>,
        count: Int
    ): CompletableFuture<List<WorkspaceScriptResource>> = session.request(
        "script.list",
        buildJsonObject {
            put("workspaceId", workspaceId)
            cursor?.let { put("cursor", it) }
        },
        WorkspaceScriptList.serializer()
    ).thenCompose { result ->
        val next = result.nextCursor?.takeIf { it.isNotEmpty() && it != cursor }
        if (next == null || count + 1 >= MAX_PAGES) {
            CompletableFuture.completedFuture(rows + result.scripts)
        } else {
            page(next, rows + result.scripts, count + 1)
        }
    }

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

    override fun create(name: String, command: String, mode: String) = session.request(
        "script.create",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("name", name)
            put("command", command)
            put("mode", mode)
        },
        WorkspaceScriptResource.serializer()
    )

    override fun remove(id: String) = session.request(
        "script.remove",
        params(id),
        WorkspaceScriptActionResult.serializer()
    )

    override fun output(id: String): CompletableFuture<String> = session.request(
        "script.output",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("scriptId", id)
            put("maxLines", OUTPUT_LINES)
        },
        String.serializer()
    )

    override fun discover(): CompletableFuture<List<WorkspaceScriptRow>> = session.request(
        "file.list",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("path", ".")
        }
    ).thenCompose { listing ->
        val names = (listing as? JsonArray).orEmpty().mapNotNull {
            ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.content
        }.toSet()
        readManifests(WorkspaceScriptDiscovery.manifestPaths.filter { it in names }, emptyMap())
            .thenApply { WorkspaceScriptDiscovery.scripts(workspaceId, it, names) }
    }.exceptionally { emptyList() }

    private fun readManifests(
        paths: List<String>,
        files: Map<String, String>
    ): CompletableFuture<Map<String, String>> {
        val path = paths.firstOrNull() ?: return CompletableFuture.completedFuture(files)
        return session.request(
            "file.read",
            buildJsonObject {
                put("workspaceId", workspaceId)
                put("path", path)
            }
        ).handle { content, _ -> content?.let(::fileText) }
            .thenCompose { text ->
                readManifests(paths.drop(1), if (text == null) files else files + (path to text))
            }
    }

    override fun subscribe(changed: () -> Unit, ready: () -> Unit, failed: (Throwable) -> Unit) =
        session.subscribe(
            SubscriptionSpec(
                "events.subscribe",
                buildJsonObject {
                    put("workspaceId", workspaceId)
                    put(
                        "eventTypes",
                        JsonArray(listOf("script:state", "script:changed").map(::JsonPrimitive))
                    )
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

    private fun params(id: String) = buildJsonObject {
        put("workspaceId", workspaceId)
        put("scriptId", id)
    }
}

/** `file.read` returns a bare string, or `{ content }` when the host bounds the read. */
internal fun fileText(result: JsonElement): String? = when (result) {
    is JsonPrimitive -> result.takeIf { it.isString }?.content
    is JsonObject -> (result["content"] as? JsonPrimitive)?.takeIf {
        it.isString
    }?.jsonPrimitive?.content
    else -> null
}

private const val MAX_PAGES = 50
private const val OUTPUT_LINES = 500
