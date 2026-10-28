package dev.intent.core.protocol.attachments

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/** Files must remain unchanged until completion. Retain the key when retrying the same payload. */
class AttachmentUploadService(
    rpc: AttachmentRpc,
    workspaceId: String,
    private val executor: Executor = ForkJoinPool.commonPool()
) {
    private val calls = AttachmentCalls(rpc, workspaceId)

    fun placeData(
        data: ByteArray,
        fileName: String,
        mimeType: String? = null,
        idempotencyKey: String? = null,
        onProgress: (TransferProgress) -> Unit = {}
    ): CompletableFuture<PlacedAttachment> {
        require(data.size.toLong() <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds the 1 GiB limit" }
        val copy = data.copyOf()
        return upload(copy.size.toLong(), fileName, mimeType, idempotencyKey, false, onProgress, {
            false
        }) {
            copy.inputStream()
        }
    }

    @Suppress("LongParameterList")
    fun placeFile(
        file: File,
        fileName: String = file.name,
        mimeType: String? = null,
        idempotencyKey: String? = null,
        forceChunked: Boolean = false,
        isCancelled: () -> Boolean = { false },
        onProgress: (TransferProgress) -> Unit = {}
    ): CompletableFuture<PlacedAttachment> = upload(
        file.length(),
        fileName,
        mimeType,
        idempotencyKey,
        forceChunked,
        onProgress,
        isCancelled
    ) { file.inputStream() }

    /** Read-only recovery after a lost placement/commit reply; never replays a mutation. */
    fun recover(idempotencyKey: String): CompletableFuture<AttachmentInfo> =
        CompletableFuture.supplyAsync({
            calls.call(
                "file.getAttachmentInfo",
                calls.params { put("idempotencyKey", idempotencyKey) },
                AttachmentInfo.serializer()
            )
        }, executor)

    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    private fun upload(
        size: Long,
        fileName: String,
        mimeType: String?,
        key: String?,
        forceChunked: Boolean,
        progress: (TransferProgress) -> Unit,
        isCancelled: () -> Boolean,
        open: () -> InputStream
    ): CompletableFuture<PlacedAttachment> = CompletableFuture.supplyAsync({
        try {
            checkCancelled(isCancelled)
            require(size in 0..MAX_ATTACHMENT_BYTES) { "Attachment exceeds the 1 GiB limit" }
            require(fileName.isNotBlank()) { "Attachment name is empty" }
            require(key == null || (key.length in 1..MAX_KEY_LENGTH && key.trim() == key)) {
                "Invalid idempotency key"
            }
            progress(TransferProgress(TransferStage.PREPARING, total = size))
            val metadata = calls.params {
                put("fileName", fileName)
                mimeType?.takeIf { it.isNotBlank() }?.let { put("mimeType", it) }
                key?.let { put("idempotencyKey", it) }
            }
            val placed = if (size <= SINGLE_SHOT_BYTES && (!forceChunked || size == 0L)) {
                val bytes = open().use {
                    readExact(it, size.toInt()).also { _ ->
                        check(
                            it.read() == -1
                        )
                    }
                }
                progress(TransferProgress(TransferStage.UPLOADING, total = size))
                checkCancelled(isCancelled)
                calls.call(
                    "file.placeAttachment",
                    JsonObject(
                        metadata +
                            ("data" to kotlinx.serialization.json.JsonPrimitive(encode(bytes)))
                    ),
                    PlacedAttachment.serializer()
                )
            } else {
                chunked(size, metadata, open, progress, isCancelled)
            }
            check(placed.ok && placed.size == size && placed.attachmentId.isNotBlank()) {
                "Invalid placement response"
            }
            progress(TransferProgress(TransferStage.COMPLETE, size, size))
            placed
        } catch (failure: Exception) {
            progress(TransferProgress(TransferStage.FAILED, total = size))
            throw failure
        }
    }, executor)

    // Keep begin, chunk, commit, and abort in one cancellation-aware transaction.
    @Suppress("TooGenericExceptionCaught", "LongMethod")
    private fun chunked(
        size: Long,
        metadata: JsonObject,
        open: () -> InputStream,
        progress: (TransferProgress) -> Unit,
        isCancelled: () -> Boolean
    ): PlacedAttachment {
        val digest = MessageDigest.getInstance("SHA-256")
        open().use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            var count = input.read(buffer)
            while (count >= 0) {
                checkCancelled(isCancelled)
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        val header = calls.params {
            put("sizeBytes", size)
            put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
        }
        checkCancelled(isCancelled)
        val session = calls.call(
            "file.attachmentUpload.begin",
            JsonObject(metadata + header),
            UploadSession.serializer()
        )
        val sessionParams = calls.params { put("uploadId", session.uploadId) }
        try {
            checkCancelled(isCancelled)
            check(session.maxChunkBytes > 0 && session.uploadId.isNotBlank()) {
                "Invalid upload session"
            }
            open().use { input ->
                var sent = 0L
                var sequence = 0
                while (sent < size) {
                    checkCancelled(isCancelled)
                    val bytes =
                        readExact(
                            input,
                            minOf(
                                CHUNK_BYTES.toLong(),
                                session.maxChunkBytes.toLong(),
                                size - sent
                            ).toInt()
                        )
                    val params = calls.params {
                        put("uploadId", session.uploadId)
                        put("seq", sequence++)
                        put("data", encode(bytes))
                    }
                    calls.rpc.request("file.attachmentUpload.chunk", params).join()
                    sent += bytes.size
                    progress(TransferProgress(TransferStage.UPLOADING, sent, size))
                }
                check(input.read() == -1) { "Attachment changed during upload" }
            }
            checkCancelled(isCancelled)
            progress(TransferProgress(TransferStage.COMMITTING, size, size))
            return calls.call(
                "file.attachmentUpload.commit",
                sessionParams,
                PlacedAttachment.serializer()
            )
        } catch (failure: Exception) {
            runCatching { calls.rpc.request("file.attachmentUpload.abort", sessionParams).join() }
            throw failure
        }
    }
}

internal fun readExact(input: InputStream, length: Int): ByteArray {
    val result = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = input.read(result, offset, length - offset)
        check(count > 0) { "Attachment ended before its declared size" }
        offset += count
    }
    return result
}

internal fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
private const val HASH_BUFFER_BYTES = 1024 * 1024

private const val MAX_KEY_LENGTH = 128

private fun checkCancelled(isCancelled: () -> Boolean) {
    if (isCancelled()) throw CancellationException("Attachment upload cancelled")
}
