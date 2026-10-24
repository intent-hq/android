package dev.intent.core.protocol.attachments

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PlacedAttachment(
    val attachmentId: String,
    val fileName: String,
    val path: String,
    val size: Long,
    val mimeType: String? = null,
    val uploadedAt: String? = null,
    val ok: Boolean = true,
    val replayed: Boolean? = null
)

@Serializable
data class AttachmentInfo(
    val attachmentId: String,
    val fileName: String,
    val path: String,
    val size: Long,
    val exists: Boolean,
    val mimeType: String? = null,
    val uploadedAt: String? = null
)

@Serializable
internal data class UploadSession(val uploadId: String, val maxChunkBytes: Int)

@Serializable
internal data class FileChunk(val content: String, val bytesRead: Int, val size: Long)

enum class TransferStage { PREPARING, UPLOADING, COMMITTING, DOWNLOADING, COMPLETE, FAILED }

data class TransferProgress(
    val stage: TransferStage,
    val transferred: Long = 0,
    val total: Long = 0
)

/** Pass this object in agent.sendMessage's imageBlocks or fileBlocks array. */
fun PlacedAttachment.messageBlock(image: Boolean = false) = buildJsonObject {
    put("type", if (image) "image" else "file")
    put("attachmentId", attachmentId)
    mimeType?.let { put("mimeType", it) }
    if (!image) {
        put("fileName", fileName)
        put("size", size)
    }
}

/** Append this Markdown through the note editor's normal mutation path. */
fun PlacedAttachment.noteMarkdown(image: Boolean = false): String {
    val label = fileName.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
        .replace("\n", " ").replace("\r", " ")
    val encoded = path.split('/').joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    return "${if (image) "!" else ""}[$label](intent://local/file/$encoded)"
}
