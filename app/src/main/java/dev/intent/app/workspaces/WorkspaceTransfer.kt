package dev.intent.app.workspaces

import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One relay owns its staging IDs. An uncertain commit is never retried or source-archived. */
class WorkspaceTransfer(
    private val source: WorkspaceOperations,
    private val target: WorkspaceOperations,
    private val workspaceId: String
) {
    private var exportId: String? = null
    private var importId: String? = null
    private var commitSent = false
    private var committed = false

    fun preview(): JsonObject =
        source.request("workspace.transfer.plan", sourceParams()).obj("plan")

    @Suppress("TooGenericExceptionCaught") // Every failure must settle owned staging.
    fun run(archiveSource: Boolean): String {
        check(source !== target) { "Choose a different destination" }
        val plan = preview()
        preflight(plan)
        val events = LinkedBlockingQueue<JsonObject>(MAX_EVENTS)
        val lease = source.events(workspaceId) { payload ->
            val event = payload["event"] as? JsonObject ?: payload
            if (event["type"] == JsonPrimitive("workspace:transfer:ready") ||
                event["type"] == JsonPrimitive("workspace:transfer:failed")
            ) {
                events.offer(event)
            }
        }
        try {
            exportId = source.request("workspace.export.start", sourceParams()).string("exportId")
            val archive = awaitArchive(events)
            require(
                archive.obj("manifest").string("creatingIntentdVersion") ==
                    plan.obj("manifest").string("creatingIntentdVersion")
            )
            stage(archive)
            commitSent = true
            val result = target.request("workspace.import.commit", importParams())
            require(result.obj("workspace").string("id") == workspaceId)
            committed = true
            return finalizeSource(archiveSource)
        } catch (error: Exception) {
            cleanup()
            if (commitSent && !committed) {
                throw IllegalStateException(
                    "The destination did not confirm import. Check it before retrying. The source was not archived.",
                    error
                )
            }
            throw error
        } finally {
            lease.close()
        }
    }

    private fun preflight(plan: JsonObject) {
        listOf(source, target).forEach {
            require(it.request("principal.me")["isAdministrator"] == JsonPrimitive(true)) {
                "Transfer requires owner access on both hosts"
            }
        }
        require(
            target.request("system.status").string("version") ==
                plan.obj("manifest").string("creatingIntentdVersion")
        ) {
            "Both hosts must run the same version"
        }
        require(
            target.request(
                "workspace.list",
                buildJsonObject {
                    put("includeArchived", true)
                    put("includeRetired", true)
                }
            )
                .rows("workspaces").none {
                    it.string("id") == workspaceId
                }
        ) { "Workspace already exists on the destination" }
    }

    private fun awaitArchive(events: LinkedBlockingQueue<JsonObject>): JsonObject {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(BUILD_MINUTES)
        while (true) {
            val remaining = deadline - System.nanoTime()
            check(remaining > 0) { "Export timed out" }
            val event = events.poll(remaining, TimeUnit.NANOSECONDS) ?: error("Export timed out")
            val data = event.obj("data")
            if (data.string("exportId") != exportId ||
                data.string("workspaceId") != workspaceId
            ) {
                continue
            }
            check(event.string("type") == "workspace:transfer:ready") { "Export failed" }
            require(data.obj("manifest").string("workspaceId") == workspaceId)
            return data
        }
    }

    private fun stage(archive: JsonObject) {
        val size = archive.number("archiveSizeBytes")
        val chunkBytes = archive.number("maxChunkBytes")
        val count = archive.number("totalChunks")
        require(
            size > 0 && chunkBytes in 1..MAX_CHUNK_BYTES && count == (size - 1) / chunkBytes + 1
        )
        val begun = target.request(
            "workspace.import.begin",
            buildJsonObject {
                put("manifest", archive.obj("manifest"))
                put("archiveSizeBytes", size)
                put("archiveSha256", archive.string("archiveSha256"))
            }
        )
        importId = begun.string("importId")
        require(begun.number("maxChunkBytes") >= chunkBytes)
        var received = 0L
        for (seq in 0 until count) {
            val chunk = source.request("workspace.export.read", sourceParams { put("seq", seq) })
            require(
                chunk.string("exportId") == exportId &&
                    chunk.number("seq") == seq &&
                    chunk.number("totalChunks") == count
            )
            val encoded = chunk.string("data")
            require(
                encoded.length <=
                    ((chunkBytes + BASE64_DECODED - 1) / BASE64_DECODED) * BASE64_ENCODED
            )
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size.toLong() == minOf(chunkBytes, size - received))
            val receipt = target.request(
                "workspace.import.chunk",
                importParams {
                    put("seq", seq)
                    put("data", encoded)
                }
            )
            received += bytes.size
            require(
                receipt.string("importId") == importId &&
                    receipt.number("seq") == seq &&
                    receipt.number("receivedBytes") == received
            )
        }
        require(received == size)
    }

    private fun finalizeSource(archiveSource: Boolean): String = try {
        val result = source.request(
            "workspace.export.finalize",
            sourceParams {
                put("archiveSource", archiveSource)
            }
        )
        require(result.string("exportId") == exportId && result["finalized"] == JsonPrimitive(true))
        "Workspace transferred."
    } catch (_: Exception) {
        "Workspace imported. Source finalization was not confirmed. Check the source; do not repeat the transfer."
    }

    private fun cleanup() {
        if (importId != null &&
            !commitSent
        ) {
            runCatching { target.request("workspace.import.abort", importParams()) }
        }
        if (exportId !=
            null
        ) {
            runCatching { source.request("workspace.export.abort", sourceParams()) }
        }
    }

    private fun sourceParams(
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
        }
    ): JsonObject = buildJsonObject {
        put("workspaceId", workspaceId)
        exportId?.let { put("exportId", it) }
        extra()
    }

    private fun importParams(
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
        }
    ): JsonObject = buildJsonObject {
        put("importId", requireNotNull(importId))
        extra()
    }
}

private const val MAX_EVENTS = 16
private const val BUILD_MINUTES = 10L
private const val MAX_CHUNK_BYTES = 16L * 1024 * 1024

private const val BASE64_DECODED = 3
private const val BASE64_ENCODED = 4
