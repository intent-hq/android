package dev.intent.app.workspaces

import android.content.Context
import dev.intent.app.attachments.AndroidAttachmentSource
import dev.intent.app.attachments.AttachmentDescription
import dev.intent.app.attachments.PhotoAttachmentSource
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.PlacedAttachment
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.attachments.TransferProgress
import dev.intent.core.protocol.session.RpcSession
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable
data class CreationMedia(
    val key: String,
    val path: String,
    val name: String,
    val mimeType: String,
    val image: Boolean
)

/** Reuses the shared picker/importer. Placement waits until workspace.create returns an ID. */
class CreationMediaSource(context: Context, session: RpcSession) : PhotoAttachmentSource {
    private val importer = AndroidAttachmentSource(
        context,
        AttachmentUploadService(RpcAttachmentClient(session), "")
    )
    private val directory = File(context.noBackupFilesDir, "creation-media").also { it.mkdirs() }
    private val descriptions = ConcurrentHashMap<File, AttachmentDescription>()
    override fun prepare(uri: String): CompletableFuture<File> = prepare(uri, uri)
    override fun prepare(uri: String, key: String): CompletableFuture<File> =
        retain(importer.prepare(uri, key), key)
    override fun prepareFile(uri: String, key: String): CompletableFuture<File> =
        retain(importer.prepareFile(uri, key), key)
    private fun retain(future: CompletableFuture<File>, key: String): CompletableFuture<File> =
        future.thenApply { file ->
            val destination = File(directory, key)
            file.copyTo(destination, overwrite = true)
            descriptions[destination] = importer.describe(file)
            importer.cleanup(file)
            destination
        }
    override fun describe(file: File): AttachmentDescription = requireNotNull(descriptions[file])
    override fun upload(
        file: File,
        key: String,
        progress: (TransferProgress) -> Unit
    ): CompletableFuture<PlacedAttachment> = CompletableFuture.completedFuture(
        PlacedAttachment(
            key,
            describe(file).name,
            file.path,
            file.length(),
            describe(file).mimeType
        )
    )
    override fun cancel(key: String) = importer.cancel(key)
    override fun release(key: String) = importer.release(key)
    override fun cleanup(file: File) {
        descriptions.remove(file)
        file.delete()
    }
}
