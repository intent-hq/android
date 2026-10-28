package dev.intent.app.attachments

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.intent.core.protocol.attachments.AttachmentDownloadService
import dev.intent.core.protocol.attachments.AttachmentRpc
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

data class MediaReference(
    val name: String,
    val image: Boolean,
    val attachmentId: String? = null,
    val path: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val asset: WorkspaceAsset? = null
)

fun interface MediaSource {
    fun load(reference: MediaReference): CompletableFuture<File>
}

/** All remote bytes use the admitted RPC session; no HTTP image client receives credentials. */
class AttachmentMedia(
    private val rpc: AttachmentRpc,
    private val workspaceId: String,
    directory: File,
    scope: String
) : MediaSource {
    private val downloads = AttachmentDownloadService(rpc, workspaceId, directory, scope)
    private val directory = File(directory, digest("$scope\u0000$workspaceId\u0000media"))

    override fun load(reference: MediaReference): CompletableFuture<File> {
        reference.attachmentId?.let { return downloads.download(it) }
        return CompletableFuture.supplyAsync {
            synchronized(this) {
                check(directory.isDirectory || directory.mkdirs())
                val identity =
                    reference.asset?.let { "asset\u0000${it.workspaceId}\u0000${it.assetId}" }
                        ?: reference.path?.let { "file\u0000$it" }
                        ?: "data\u0000${requireNotNull(reference.data)}"
                val target = File(directory, digest(identity))
                if (!target.isFile) {
                    val temporary = File.createTempFile("media-", ".part", directory)
                    try {
                        if (reference.asset != null) {
                            readAsset(reference.asset, temporary)
                        } else if (reference.path != null) {
                            readPath(
                                reference.path,
                                temporary,
                                dev.intent.app.files.previewByteLimit(
                                    mediaPreviewKind(reference)
                                ).toLong()
                            )
                        } else {
                            val data = requireNotNull(reference.data)
                            check(data.length <= MAX_ENCODED_BYTES)
                            temporary.writeBytes(Base64.getDecoder().decode(data))
                        }
                        check(temporary.renameTo(target))
                    } finally {
                        temporary.delete()
                    }
                }
                target
            }
        }
    }

    private fun readAsset(asset: WorkspaceAsset, target: File) {
        require(asset.isSafe())
        val response = rpc.request(
            "note.readAsset",
            buildJsonObject {
                put("workspaceId", asset.workspaceId)
                put("asset", asset.assetId)
            }
        ).join()
        val encoded = response.getValue("data").jsonPrimitive.content
        check(encoded.length <= MAX_ENCODED_BYTES)
        val bytes = Base64.getDecoder().decode(encoded)
        check(bytes.size <= MAX_ASSET_BYTES)
        target.writeBytes(bytes)
    }

    private fun readPath(path: String, target: File, limit: Long) {
        require(safeWorkspacePath(path))
        var offset = 0L
        var expectedSize: Long? = null
        target.outputStream().use { output ->
            do {
                val chunk = rpc.request(
                    "file.readChunk",
                    buildJsonObject {
                        put("workspaceId", workspaceId)
                        put("path", path)
                        put("offset", offset)
                        put("length", READ_BYTES)
                    }
                ).join()
                val size = chunk.getValue("size").jsonPrimitive.long
                check(size in 0..limit && (expectedSize == null || expectedSize == size))
                expectedSize = size
                val bytes = Base64.getDecoder().decode(
                    chunk.getValue("content").jsonPrimitive.content
                )
                check(bytes.size == chunk.getValue("bytesRead").jsonPrimitive.int)
                check(bytes.size <= READ_BYTES && offset + bytes.size <= size)
                check(bytes.isNotEmpty() || offset == size)
                output.write(bytes)
                offset += bytes.size
            } while (offset < requireNotNull(expectedSize))
        }
    }
}

fun safeWorkspacePath(path: String): Boolean = path.isNotBlank() &&
    !path.startsWith('/') &&
    '\\' !in path &&
    '\u0000' !in path &&
    path.split('/').none { it == ".." || it == "." || it.isEmpty() }

fun openMedia(context: Context, file: File, reference: MediaReference) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.attachments", file)
    val mime =
        reference.mimeType
            ?: java.net.URLConnection.guessContentTypeFromName(reference.path ?: reference.name)
            ?: if (reference.image) "image/*" else "application/octet-stream"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Open attachment"))
}

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
private const val READ_BYTES = 1024 * 1024
private const val MAX_ENCODED_BYTES = 40 * 1024 * 1024

private const val MAX_ASSET_BYTES = 32 * 1024 * 1024
