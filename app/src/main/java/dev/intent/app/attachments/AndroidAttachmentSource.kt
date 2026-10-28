package dev.intent.app.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.PlacedAttachment
import dev.intent.core.protocol.attachments.TransferProgress
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Copies provider data into owned storage. No provider grant is needed after preparation. */
@Suppress("TooManyFunctions") // Provider imports, owned copies, and cancellable upload lifecycle.
class AndroidAttachmentSource(
    private val context: Context,
    private val upload: AttachmentUploadService
) : PhotoAttachmentSource {
    private val photos = AndroidPhotoSource(context)
    private val descriptions = ConcurrentHashMap<File, AttachmentDescription>()
    private val cancellations = ConcurrentHashMap<String, AtomicBoolean>()

    override fun prepare(uri: String): CompletableFuture<File> = prepare(uri, uri)

    override fun prepare(uri: String, key: String): CompletableFuture<File> =
        import(uri, key, false)

    override fun prepareFile(uri: String, key: String): CompletableFuture<File> =
        import(uri, key, true)

    private fun import(uri: String, key: String, asFile: Boolean): CompletableFuture<File> {
        val cancelled = AtomicBoolean()
        cancellations[key] = cancelled
        return CompletableFuture.supplyAsync {
            val source = Uri.parse(uri)
            val description = description(source).let { if (asFile) it.copy(image = false) else it }
            val file = if (description.image) {
                photos.prepareImage(uri, cancelled::get).join()
            } else {
                copy(source, description, cancelled)
            }
            if (cancelled.get()) {
                file.delete()
                cancellations.remove(key, cancelled)
                throw CancellationException("Attachment import cancelled")
            }
            descriptions[file] = if (description.image) {
                AttachmentDescription(
                    "${description.name.substringBeforeLast('.')}.jpg",
                    "image/jpeg"
                )
            } else {
                description
            }
            file
        }.whenComplete { _, error -> if (error != null) cancellations.remove(key, cancelled) }
    }

    override fun describe(file: File): AttachmentDescription = requireNotNull(descriptions[file])

    override fun upload(
        file: File,
        key: String,
        progress: (TransferProgress) -> Unit
    ): CompletableFuture<PlacedAttachment> {
        val description = describe(file)
        runCatching { description.validateSize(file.length()) }.exceptionOrNull()?.let {
            return CompletableFuture<PlacedAttachment>().apply {
                completeExceptionally(it)
            }
        }
        val cancelled = cancellations.computeIfAbsent(key) { AtomicBoolean() }
        return upload.placeFile(
            file,
            description.name,
            description.mimeType,
            key,
            forceChunked = description.isVideo,
            isCancelled = cancelled::get,
            onProgress = progress
        ).whenComplete { _, _ -> cancellations.remove(key, cancelled) }
    }

    override fun cancel(key: String) {
        cancellations[key]?.set(true)
    }

    override fun release(key: String) {
        cancellations.remove(key)
    }

    override fun cleanup(file: File) {
        descriptions.remove(file)
        file.delete()
    }

    private fun description(uri: Uri): AttachmentDescription {
        val resolver = context.contentResolver
        val name =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
                ?: "attachment"
        val extensionMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            name.substringAfterLast('.').lowercase()
        )
        val reportedMime = resolver.getType(uri)
        val mime = if (extensionMime?.startsWith("video/") == true &&
            reportedMime?.startsWith("video/", ignoreCase = true) != true
        ) {
            extensionMime
        } else {
            reportedMime ?: extensionMime ?: "application/octet-stream"
        }
        return AttachmentDescription(name, mime).let { it.copy(image = it.image && !it.isVideo) }
    }

    // Remove the owned copy after every provider failure.
    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
    private fun copy(uri: Uri, description: AttachmentDescription, cancelled: AtomicBoolean): File {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) description.validateSize(it.getLong(0))
        }
        val directory = File(context.cacheDir, "attachment-imports").also {
            check(
                it.isDirectory || it.mkdirs()
            )
        }
        val file = File.createTempFile("attachment-", ".data", directory)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                file.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var total = 0L
                    while (true) {
                        if (cancelled.get()) {
                            throw CancellationException(
                                "Attachment import cancelled"
                            )
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        description.validateSize(total)
                        output.write(buffer, 0, count)
                    }
                }
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}

private const val COPY_BUFFER = 64 * 1024
