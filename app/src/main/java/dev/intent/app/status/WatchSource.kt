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

interface WatchSource {
    val scriptsSupported: Boolean
    fun list(family: WatchFamily, cursor: String?): CompletableFuture<WatchPage>
    fun detail(id: String): CompletableFuture<JsonObject>
    fun subscribe(
        family: WatchFamily,
        event: (JsonObject) -> Unit,
        ready: () -> Unit,
        error: (Throwable) -> Unit
    ): Closeable
}

class RpcWatchSource(private val session: RpcSession, private val workspaceId: String) :
    WatchSource {
    override val scriptsSupported: Boolean get() = session.serverCapabilities.number(
        "scriptMonitors"
    ) ==
        2L &&
        session.serverCapabilities.number("scriptActiveLists") == 1L

    override fun list(family: WatchFamily, cursor: String?): CompletableFuture<WatchPage> =
        session.request(
            "${family.wire}.list",
            buildJsonObject {
                put("workspaceId", workspaceId)
                if (family == WatchFamily.SCRIPT) {
                    put("view", "summary")
                    put("state", "active")
                    put("limit", WATCH_PAGE_SIZE)
                    cursor?.let { put("cursor", it) }
                }
            }
        ).thenApply { result ->
            val body = result as? JsonObject ?: error("Invalid watch list")
            if (family == WatchFamily.SCRIPT) require(body.text("view") == "summary")
            val rows = body[family.listKey] as? JsonArray ?: error("Invalid watch list")
            if (family == WatchFamily.SCRIPT) require(rows.size <= WATCH_PAGE_SIZE)
            WatchPage(
                rows.map {
                    val row = it as? JsonObject ?: error("Invalid watch")
                    require(
                        row.text("workspaceId") == workspaceId &&
                            !row.text(family.idKey).isNullOrBlank()
                    )
                    if (family == WatchFamily.SCRIPT) scriptSummary(row) else row
                },
                body.text("nextCursor")
            )
        }

    override fun detail(id: String): CompletableFuture<JsonObject> = session.request(
        "scriptMonitor.get",
        buildJsonObject {
            put("workspaceId", workspaceId)
            put("monitorId", id)
        }
    ).thenApply {
        val row = it as? JsonObject ?: error("Invalid watch details")
        require(
            row.text("workspaceId") == workspaceId &&
                row.text("monitorId") == id &&
                row.text("state") == "active"
        )
        scriptSummary(row)
        row
    }

    override fun subscribe(
        family: WatchFamily,
        event: (JsonObject) -> Unit,
        ready: () -> Unit,
        error: (Throwable) -> Unit
    ): Closeable = session.subscribe(
        SubscriptionSpec(
            "events.subscribe",
            buildJsonObject {
                if (family != WatchFamily.PR) put("workspaceId", workspaceId)
                put("eventTypes", JsonArray(listOf(JsonPrimitive("${family.wire}:*"))))
            },
            notificationMethod = "events.event"
        ),
        onMessage = { payload ->
            val envelope = payload.objectValue("event") ?: payload
            val data = envelope.objectValue("data")
            val workspace = data?.text("workspaceId") ?: envelope.text("workspaceId")
            val nested = data?.objectValue("monitor")
            val matches = workspace == workspaceId &&
                (
                    envelope.text(
                        "workspaceId"
                    ) == null ||
                        envelope.text("workspaceId") == workspaceId
                    ) &&
                (family != WatchFamily.SCRIPT || nested?.text("workspaceId") == workspaceId)
            if (matches) {
                runCatching {
                    if (family == WatchFamily.SCRIPT &&
                        envelope.text("type") == "scriptMonitor:registered"
                    ) {
                        val summary = scriptSummary(requireNotNull(nested))
                        event(
                            JsonObject(
                                envelope +
                                    (
                                        "data" to JsonObject(
                                            requireNotNull(data) + ("monitor" to summary)
                                        )
                                        )
                            )
                        )
                    } else if (family == WatchFamily.SCRIPT) {
                        val identity = requireNotNull(nested).filterKeys {
                            it in setOf("monitorId", "runId", "workspaceId")
                        }
                        val normalized = JsonObject(
                            requireNotNull(data) + ("monitor" to JsonObject(identity))
                        )
                        event(JsonObject(envelope + ("data" to normalized)))
                    } else {
                        event(envelope)
                    }
                }.onFailure(error)
            }
        },
        onSubscribed = { ready() },
        onError = error
    )
}

private const val WATCH_PAGE_SIZE = 100
