package dev.intentapp.android.workspaces

import dev.intent.core.protocol.session.RpcException
import dev.intent.core.protocol.session.SessionDisconnectedException
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
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

    /** Returns the source-finalization warning, if any (iOS `WorkspaceTransferRelay.run`). */
    @Suppress("TooGenericExceptionCaught") // Every failure must settle owned staging.
    fun run(archiveSource: Boolean): String? {
        check(!started) { "A transfer attempt cannot be reused" }
        started = true
        var lease: Closeable? = null
        try {
            control.checkCancellation()
            ensure(source !== target) { "Choose a different machine for the transfer." }
            val plan = transferPlan(
                source.request("workspace.transfer.plan", sourceParams()),
                workspaceId
            )
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
            ensure(
                (archive["manifest"] as? JsonObject)?.optional("creatingIntentdVersion") ==
                    plan.version
            ) { "The source version changed. Refresh the transfer preview and try again." }
            stage(archive)
            control.beginCommit()
            commitSent = true
            val result = target.request("workspace.import.commit", importParams())
            ensure((result["workspace"] as? JsonObject)?.optional("id") == workspaceId) {
                "The destination returned an invalid completion response."
            }
            committed = true
            control.update(
                control.state.value.copy(
                    phase = TransferPhase.FINALIZING,
                    message = "Settling the source workspace…"
                )
            )
            val warning = finalizeSource(archiveSource)
            control.finish(TransferPhase.DONE, "Workspace transferred.", warning)
            return warning
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
        val detail = if (cancelled) "Transfer cancelled." else transferErrorText(error)
        when (phase) {
            TransferPhase.UNCERTAIN -> control.finish(
                phase,
                TRANSFER_FAILED,
                "The destination did not confirm the transfer. It may already contain the " +
                    "workspace. Check it before trying again. The source was not archived. " +
                    detail + warning
            )
            TransferPhase.CANCELLED -> control.finish(phase, TRANSFER_CANCELLED)
            else -> control.finish(phase, TRANSFER_FAILED, detail + warning)
        }
        if (phase == TransferPhase.UNCERTAIN) {
            throw TransferException(requireNotNull(control.state.value.error), error)
        }
        throw error
    }

    private fun preflight(plan: TransferPlan) {
        listOf(source, target).forEach {
            control.checkCancellation()
            ensure(it.request("principal.me")["isAdministrator"] == JsonPrimitive(true)) {
                "Workspace transfer requires owner access on both machines."
            }
        }
        ensure(target.request("system.status").optional("version") == plan.version) {
            "Both machines must run the same host version. Update them before transferring."
        }
        val rows = runCatching {
            target.request(
                "workspace.list",
                buildJsonObject {
                    put("includeArchived", true)
                    put("includeRetired", true)
                }
            ).rows("workspaces")
        }.getOrElse {
            if (it !is IllegalStateException) throw it
            fault("The destination could not check for an existing workspace.")
        }
        ensure(rows.none { it.optional("id") == workspaceId }) {
            "This workspace already exists on that machine. Choose another destination."
        }
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
            ensure(remaining > 0) { "The source took too long to prepare the transfer. Try again." }
            val event = events.poll(
                minOf(remaining, TimeUnit.MILLISECONDS.toNanos(CANCEL_POLL_MS)),
                TimeUnit.NANOSECONDS
            ) ?: continue
            val data = event["data"] as? JsonObject
            if (data != null &&
                data.optional("exportId") == exportId &&
                data.optional("workspaceId") == workspaceId
            ) {
                ensure(event.optional("type") == "workspace:transfer:ready") {
                    data.optional("reason") ?: "The source could not prepare the transfer."
                }
                ensure((data["manifest"] as? JsonObject)?.optional("workspaceId") == workspaceId) {
                    INVALID_ARCHIVE
                }
                return data
            }
        }
    }

    private fun stage(archive: JsonObject) {
        val size = archive.optionalNumber("archiveSizeBytes") ?: fault(INVALID_ARCHIVE)
        val chunkBytes = archive.optionalNumber("maxChunkBytes") ?: fault(INVALID_ARCHIVE)
        val count = archive.optionalNumber("totalChunks") ?: fault(INVALID_ARCHIVE)
        ensure(
            size > 0 &&
                chunkBytes in 1..MAX_CHUNK_BYTES &&
                count == (size - 1) / chunkBytes + 1 &&
                archive.optional("archiveSha256") != null
        ) { INVALID_ARCHIVE }
        control.checkCancellation()
        control.update(
            TransferProgress(TransferPhase.IMPORTING, "Preparing the destination…", 0, size)
        )
        beginImport(archive, size, chunkBytes)
        var received = 0L
        for (seq in 0 until count) {
            control.checkCancellation()
            val encoded = validChunk(
                source.request("workspace.export.read", sourceParams { put("seq", seq) }),
                ChunkExpectation(requireNotNull(exportId), seq, count, chunkBytes, size - received)
            )
            control.checkCancellation()
            val receipt = target.request(
                "workspace.import.chunk",
                importParams {
                    put("seq", seq)
                    put("data", encoded)
                }
            )
            received += minOf(chunkBytes, size - received)
            ensure(
                receipt.optional("importId") == importId &&
                    receipt.optionalNumber("seq") == seq &&
                    receipt.optionalNumber("receivedBytes") == received
            ) { "The destination did not confirm receipt of the transfer archive." }
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
        ensure(received == size) { "The transfer archive was incomplete. Try again." }
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
        importId = begun.optional("importId")
            ?: fault("The host returned an invalid transfer response.")
        control.checkCancellation()
        ensure((begun.optionalNumber("maxChunkBytes") ?: 0) >= chunkBytes) {
            "The destination cannot receive this archive’s transfer chunks. Update both hosts."
        }
    }

    @Suppress("TooGenericExceptionCaught") // Any finalize failure becomes a completion warning.
    private fun finalizeSource(archiveSource: Boolean): String? = try {
        val result = source.request(
            "workspace.export.finalize",
            sourceParams {
                put("archiveSource", archiveSource)
            }
        )
        ensure(
            result.optional("exportId") == exportId && result["finalized"] == JsonPrimitive(true)
        ) { "The source did not confirm finalization." }
        null
    } catch (error: Exception) {
        "The workspace was transferred successfully. The source could not be finalized; " +
            "check its archive status on the source machine. Do not repeat the transfer. " +
            transferErrorText(error) + cleanup()
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
            " Transfer cleanup could not be confirmed on the ${failures.joinToString(" and ")}."
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

/** A transfer failure whose message is shown to the user as-is. */
class TransferException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** iOS `WorkspaceTransferPlan`: the preview a transfer is checked against. */
data class TransferPlan(val estimatedBytes: Long, val warnings: List<String>, val version: String)

internal fun transferPlan(response: JsonObject, workspaceId: String): TransferPlan {
    val invalid = "The host could not provide a transfer preview. Update the host and try again."
    val plan = response["plan"] as? JsonObject ?: fault(invalid)
    val manifest = plan["manifest"] as? JsonObject ?: fault(invalid)
    val version = manifest.optional("creatingIntentdVersion") ?: fault(invalid)
    val bytes = plan.optionalNumber("totalSizeBytes")?.takeIf { it >= 0 } ?: fault(invalid)
    val warnings = plan["warnings"] as? JsonArray ?: fault(invalid)
    ensure(manifest.optional("workspaceId") == workspaceId) { invalid }
    return TransferPlan(
        bytes,
        warnings.mapNotNull { (it as? JsonObject)?.let(::transferWarningText) },
        version
    )
}

/** User-facing text for a transfer error, matching iOS `localizedDescription` copy. */
internal fun transferErrorText(error: Throwable): String = when (error) {
    is TransferException -> error.message.orEmpty()
    is ExecutionException -> error.cause?.let(::transferErrorText) ?: INVALID_RESPONSE
    is RpcException -> error.error.message.takeIf { it.isNotBlank() } ?: INVALID_RESPONSE
    is SessionDisconnectedException ->
        "The host disconnected during transfer. Reconnect and check the workspace."
    is TimeoutException -> "The host took too long to respond. Try again."
    else -> INVALID_RESPONSE
}

internal data class ChunkExpectation(
    val exportId: String,
    val seq: Long,
    val count: Long,
    val chunkBytes: Long,
    val remaining: Long
)

/** Returns the validated base64 payload of one `workspace.export.read` reply. */
internal fun validChunk(chunk: JsonObject, expected: ChunkExpectation): String {
    val encoded = chunk.optional("data")
    val maximum =
        ((expected.chunkBytes + BASE64_DECODED - 1) / BASE64_DECODED) * BASE64_ENCODED
    val decoded = encoded?.takeIf { it.length <= maximum }
        ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
    val header = ChunkExpectation(
        chunk.optional("exportId").orEmpty(),
        chunk.optionalNumber("seq") ?: -1,
        chunk.optionalNumber("totalChunks") ?: -1,
        expected.chunkBytes,
        decoded?.size?.toLong() ?: -1
    )
    val wanted = expected.copy(remaining = minOf(expected.chunkBytes, expected.remaining))
    ensure(header == wanted) { "The source returned an invalid archive chunk. Try again." }
    return requireNotNull(encoded)
}

internal fun fault(message: String): Nothing = throw TransferException(message)

private inline fun ensure(condition: Boolean, message: () -> String) {
    if (!condition) fault(message())
}

private fun JsonObject.optional(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.optionalNumber(key: String): Long? =
    (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal const val TRANSFER_FAILED = "The transfer could not finish."
internal const val TRANSFER_CANCELLED =
    "Transfer cancelled. The source workspace is still available; " +
        "stopped agents can be restarted there."
private const val INVALID_RESPONSE = "The host returned an invalid transfer response."
private const val INVALID_ARCHIVE = "The source returned an invalid transfer archive."
private const val CANCEL_POLL_MS = 100L
private const val MAX_EVENTS = 16
private const val BUILD_MINUTES = 10L
private const val MAX_CHUNK_BYTES = 16L * 1024 * 1024

private const val BASE64_DECODED = 3
private const val BASE64_ENCODED = 4
