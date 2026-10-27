package dev.intent.app.files

import dev.intent.app.attachments.safeWorkspacePath
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

internal const val FILE_CHUNK_BYTES = 4 * 1024 * 1024
internal const val TEXT_MAX_BYTES = 5 * 1024 * 1024
internal const val IMAGE_MAX_BYTES = 32 * 1024 * 1024
internal const val IMAGE_MAX_EDGE = 2048

enum class WorkspacePreviewKind { TEXT, IMAGE, PDF, VIDEO, HTML, UNSUPPORTED }

internal const val DOCUMENT_MAX_BYTES = 200 * 1024 * 1024

internal fun previewByteLimit(kind: WorkspacePreviewKind): Int = when (kind) {
    WorkspacePreviewKind.TEXT, WorkspacePreviewKind.HTML -> TEXT_MAX_BYTES
    WorkspacePreviewKind.PDF, WorkspacePreviewKind.VIDEO -> DOCUMENT_MAX_BYTES
    else -> IMAGE_MAX_BYTES
}

fun workspacePreviewKind(path: String): WorkspacePreviewKind =
    when (path.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "webp" -> WorkspacePreviewKind.IMAGE
        "pdf" -> WorkspacePreviewKind.PDF
        "html", "htm" -> WorkspacePreviewKind.HTML
        "mp4", "webm", "mov", "m4v" -> WorkspacePreviewKind.VIDEO
        "zip", "gz", "tar",
        "woff", "woff2", "ttf", "exe", "so", "aar" ->
            WorkspacePreviewKind.UNSUPPORTED
        else -> WorkspacePreviewKind.TEXT
    }

fun interface WorkspaceFileRpc {
    fun request(method: String, params: JsonObject): CompletableFuture<JsonElement>
}

/** Uses only the paired RPC session. Each preview owns one bounded temporary download. */
class WorkspaceFileSource(private val rpc: WorkspaceFileRpc, private val workspaceId: String) {
    fun list(path: String): CompletableFuture<List<WorkspaceFileEntry>> = rpc.request(
        "file.list",
        params(path)
    ).thenApply { parseFileList(it, path) }

    @Suppress("TooGenericExceptionCaught") // Always remove incomplete downloads.
    fun download(path: String, directory: File, limit: Int): File {
        require(safeWorkspacePath(path))
        check(directory.isDirectory || directory.mkdirs())
        val file = File(directory, path.substringAfterLast('/'))
        try {
            file.outputStream().use { output ->
                var offset = 0L
                var expected: Long? = null
                do {
                    val result = rpc.request(
                        "file.readChunk",
                        params(path, offset)
                    ).get().jsonObject
                    val size = result.getValue("size").jsonPrimitive.long
                    check(size in 0..limit.toLong()) { "File exceeds the preview size limit." }
                    check(expected == null || expected == size) {
                        "File changed during download. Retry."
                    }
                    expected = size
                    val content = result.getValue("content").jsonPrimitive.content
                    check(content.length <= MAX_ENCODED_CHUNK)
                    val bytes = Base64.getDecoder().decode(content)
                    check(bytes.size == result.getValue("bytesRead").jsonPrimitive.int)
                    check(bytes.size <= FILE_CHUNK_BYTES && bytes.size <= size - offset)
                    check(bytes.isNotEmpty() || offset == size)
                    output.write(bytes)
                    offset += bytes.size
                } while (offset < size)
            }
            return file
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    private fun params(path: String, offset: Long? = null) = buildJsonObject {
        put("workspaceId", workspaceId)
        put("path", path)
        offset?.let {
            put("offset", it)
            put("length", FILE_CHUNK_BYTES)
        }
    }
}

internal fun readWorkspaceText(file: File): String {
    check(file.length() <= TEXT_MAX_BYTES)
    val bytes = file.readBytes()
    check(0.toByte() !in bytes) { "This file contains binary data." }
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
}

private const val MAX_ENCODED_CHUNK = (FILE_CHUNK_BYTES + 2) / 3 * 4
