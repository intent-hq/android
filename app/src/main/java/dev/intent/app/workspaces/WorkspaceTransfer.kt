package dev.intent.app.workspaces

import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One relay owns its staging IDs. An uncertain commit is never retried or source-archived. */
class WorkspaceTransfer(
    private val source: WorkspaceOperations,
    private val target: WorkspaceOperations,
    private val workspaceId: String,
    val control: TransferControl = TransferControl()
) {
    private var exportId: String? = null
    private var importId: String? = null
    private var commitSent = false
    private var committed = false
    private var exportStartSent = false
    private var importBeginSent = false
    private var started = false

    @Suppress("TooGenericExceptionCaught") // Every failure must settle owned staging.
    fun run(archiveSource: Boolean): String {
        check(!started) { "A transfer attempt cannot be reused" }
        started = true
        var lease: Closeable? = null
        try {
            control.checkCancellation()
            check(source !== target) { "Choose a different destination" }
            val plan = source.request("workspace.transfer.plan", sourceParams()).obj("plan")
            preflight(plan)
            control.checkCancellation()
            val events = LinkedBlockingQueue<JsonObject>(MAX_EVENTS)
            val progress = AtomicReference<JsonObject?>()
            lease = source.events(workspaceId) { payload ->
                val event = payload["event"] as? JsonObject ?: payload
                when (event["type"]) {
                    JsonPrimitive("workspace:transfer:progress") -> progress.set(event)
                    JsonPrimitive("workspace:transfer:ready"),
                    JsonPrimitive("workspace:transfer:failed") -> events.offer(event)
                    else -> Unit
                }
            }
            control.checkCancellation()
            control.update(TransferProgress(TransferPhase.EXPORTING, "Preparing the transfer…"))
            exportStartSent = true
            exportId = source.request("workspace.export.start", sourceParams()).string("exportId")
            control.checkCancellation()
            val archive = awaitArchive(events, progress)
            require(
                archive.obj("manifest").string("creatingIntentdVersion") ==
                    plan.obj("manifest").string("creatingIntentdVersion")
            )
            stage(archive)
            control.beginCommit()
            commitSent = true
            val result = target.request("workspace.import.commit", importParams())
            require(result.obj("workspace").string("id") == workspaceId)
            committed = true
            control.update(
                control.state.value.copy(
                    phase = TransferPhase.FINALIZING,
                    message = "Settling the source workspace…"
                )
            )
            val message = finalizeSource(archiveSource)
            control.finish(TransferPhase.DONE, message)
            return message
        } catch (error: CancellationException) {
            fail(error, cancelled = true)
        } catch (error: Exception) {
            fail(error, cancelled = false)
        } finally {
            lease?.close()
        }
    }

    private fun fail(error: Exception, cancelled: Boolean): Nothing {
        val warning = cleanup()
        val phase = when {
            commitSent && !committed -> TransferPhase.UNCERTAIN
            cancelled && warning.isEmpty() -> TransferPhase.CANCELLED
            else -> TransferPhase.FAILED
        }
        val message = when (phase) {
            TransferPhase.UNCERTAIN ->
                "The destination did not confirm import. Check it before retrying. The source was not archived."
            TransferPhase.CANCELLED ->
                "Transfer cancelled. The source workspace is still available. " +
                    "Stopped agents can be restarted there."
            else ->
                "Transfer failed. The source was not archived. Check both hosts before retrying."
        } + warning
        control.finish(phase, message)
        if (phase == TransferPhase.UNCERTAIN) throw IllegalStateException(message, error)
        throw error
    }

    private fun preflight(plan: JsonObject) {
        listOf(source, target).forEach {
            control.checkCancellation()
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

    private fun awaitArchive(
        events: LinkedBlockingQueue<JsonObject>,
        progress: AtomicReference<JsonObject?>
    ): JsonObject {
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(BUILD_MINUTES)
        while (true) {
            control.checkCancellation()
            progress.getAndSet(null)?.let { event ->
                val data = event["data"] as? JsonObject
                if (data?.get("exportId") == JsonPrimitive(exportId) &&
                    data["workspaceId"] == JsonPrimitive(workspaceId)
                ) {
                    control.update(
                        TransferProgress(
                            TransferPhase.EXPORTING,
                            exportStageMessage((data["stage"] as? JsonPrimitive)?.content)
                        )
                    )
                }
            }
            val remaining = deadline - System.nanoTime()
            check(remaining > 0) { "Export timed out" }
            val event = events.poll(
                minOf(remaining, TimeUnit.MILLISECONDS.toNanos(CANCEL_POLL_MS)),
                TimeUnit.NANOSECONDS
            ) ?: continue
            val data = event.obj("data")
            if (data.string("exportId") == exportId &&
                data.string("workspaceId") == workspaceId
            ) {
                check(event.string("type") == "workspace:transfer:ready") { "Export failed" }
                require(data.obj("manifest").string("workspaceId") == workspaceId)
                return data
            }
        }
    }

    private fun stage(archive: JsonObject) {
        val size = archive.number("archiveSizeBytes")
        val chunkBytes = archive.number("maxChunkBytes")
        val count = archive.number("totalChunks")
        require(
            size > 0 && chunkBytes in 1..MAX_CHUNK_BYTES && count == (size - 1) / chunkBytes + 1
        )
        control.checkCancellation()
        control.update(
            TransferProgress(TransferPhase.IMPORTING, "Preparing the destination…", 0, size)
        )
        beginImport(archive, size, chunkBytes)
        var received = 0L
        for (seq in 0 until count) {
            control.checkCancellation()
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
            control.checkCancellation()
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
            control.checkCancellation()
            control.update(
                TransferProgress(
                    TransferPhase.IMPORTING,
                    "Transferring workspace…",
                    received,
                    size
                )
            )
        }
        require(received == size)
    }

    private fun beginImport(archive: JsonObject, size: Long, chunkBytes: Long) {
        importBeginSent = true
        val begun = target.request(
            "workspace.import.begin",
            buildJsonObject {
                put("manifest", archive.obj("manifest"))
                put("archiveSizeBytes", size)
                put("archiveSha256", archive.string("archiveSha256"))
            }
        )
        importId = begun.string("importId")
        control.checkCancellation()
        require(begun.number("maxChunkBytes") >= chunkBytes)
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
        "Workspace imported. Source finalization was not confirmed. " +
            "Check the source; do not repeat the transfer." +
            cleanup()
    }

    private fun cleanup(): String {
        val failures = mutableListOf<String>()
        if (importId != null && !commitSent) {
            runCatching { target.request("workspace.import.abort", importParams()) }
                .onFailure { failures += "destination" }
        }
        if (exportId != null) {
            runCatching { source.request("workspace.export.abort", sourceParams()) }
                .onFailure { failures += "source" }
        }
        if (exportStartSent && exportId == null) failures += "source"
        if (importBeginSent && importId == null) failures += "destination"
        return if (failures.isEmpty()) {
            ""
        } else {
            " Transfer cleanup was not confirmed on the ${failures.joinToString(" and ")}."
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

private const val CANCEL_POLL_MS = 100L
private const val MAX_EVENTS = 16
private const val BUILD_MINUTES = 10L
private const val MAX_CHUNK_BYTES = 16L * 1024 * 1024

private const val BASE64_DECODED = 3
private const val BASE64_ENCODED = 4
