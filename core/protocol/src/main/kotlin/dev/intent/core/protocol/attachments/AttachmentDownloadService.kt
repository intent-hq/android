package dev.intent.core.protocol.attachments

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import kotlinx.serialization.json.put

/** Supply the admitted certificate/principal cache scope, never a bearer token. */
class AttachmentDownloadService(
    rpc: AttachmentRpc,
    workspaceId: String,
    cacheDirectory: File,
    cacheScope: String,
    private val executor: Executor = ForkJoinPool.commonPool()
) {
    private val calls = AttachmentCalls(rpc, workspaceId)
    private val directory: File
    private val lock = Any()

    init {
        require(cacheScope.isNotBlank() && workspaceId.isNotBlank())
        directory = File(cacheDirectory, hash("$cacheScope\u0000$workspaceId"))
    }

    /** Failed or incomplete downloads are never cached. Retry by calling download again. */
    fun download(
        attachmentId: String,
        onProgress: (TransferProgress) -> Unit = {}
    ): CompletableFuture<File> = CompletableFuture.supplyAsync({
        synchronized(lock) {
            require(attachmentId.isNotBlank())
            val target = File(directory, hash(attachmentId))
            if (target.isFile) {
                onProgress(
                    TransferProgress(TransferStage.COMPLETE, target.length(), target.length())
                )
                target
            } else {
                fetch(attachmentId, target, onProgress)
            }
        }
    }, executor)

    @Suppress("TooGenericExceptionCaught") // Report every failure and always delete partial files.
    private fun fetch(id: String, target: File, progress: (TransferProgress) -> Unit): File {
        var temporary: File? = null
        try {
            val info = calls.call(
                "file.getAttachmentInfo",
                calls.params {
                    put("attachmentId", id)
                },
                AttachmentInfo.serializer()
            )
            check(info.exists) { "Attachment file is missing" }
            check(info.attachmentId == id && info.size in 0..MAX_ATTACHMENT_BYTES) {
                "Invalid attachment metadata"
            }
            check(
                info.path.startsWith(".intent/attachments/") &&
                    info.path.split('/').none { it == ".." }
            ) {
                "Invalid attachment path"
            }
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create attachment cache" }
            temporary = File.createTempFile("download-", ".part", directory)
            temporary.outputStream().use { output ->
                var offset = 0L
                while (offset < info.size) {
                    val length = minOf(DOWNLOAD_CHUNK_BYTES.toLong(), info.size - offset).toInt()
                    val chunk = calls.call(
                        "file.readChunk",
                        calls.params {
                            put("path", info.path)
                            put("offset", offset)
                            put("length", length)
                        },
                        FileChunk.serializer()
                    )
                    val bytes = Base64.getDecoder().decode(chunk.content)
                    check(
                        chunk.size == info.size &&
                            bytes.size == chunk.bytesRead &&
                            bytes.size in 1..length
                    ) {
                        "Invalid attachment download chunk"
                    }
                    output.write(bytes)
                    offset += bytes.size
                    progress(TransferProgress(TransferStage.DOWNLOADING, offset, info.size))
                }
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            progress(TransferProgress(TransferStage.COMPLETE, info.size, info.size))
            return target
        } catch (failure: Exception) {
            progress(TransferProgress(TransferStage.FAILED))
            throw failure
        } finally {
            temporary?.delete()
        }
    }
}

private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
private const val DOWNLOAD_CHUNK_BYTES = 1024 * 1024
