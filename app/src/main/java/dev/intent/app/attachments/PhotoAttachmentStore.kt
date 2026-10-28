package dev.intent.app.attachments

import dev.intent.core.protocol.attachments.PlacedAttachment
import dev.intent.core.protocol.attachments.TransferProgress
import dev.intent.core.protocol.attachments.TransferStage
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface PhotoAttachmentSource {
    fun prepare(uri: String): CompletableFuture<File>
    fun prepare(uri: String, key: String): CompletableFuture<File> = prepare(uri)
    fun prepareFile(uri: String, key: String): CompletableFuture<File> = prepare(uri, key)
    fun cancel(key: String) = Unit
    fun release(key: String) = Unit
    fun cleanup(file: File) {
        file.delete()
    }
    fun describe(file: File): AttachmentDescription =
        AttachmentDescription("photo.jpg", "image/jpeg")
    fun upload(
        file: File,
        key: String,
        progress: (TransferProgress) -> Unit
    ): CompletableFuture<PlacedAttachment>
}

data class AttachmentDescription(
    val name: String,
    val mimeType: String,
    val image: Boolean = mimeType.startsWith("image/")
)

data class PendingPhoto(
    val key: String,
    val uri: String,
    val file: File? = null,
    val description: AttachmentDescription? = null,
    val imageSelection: Boolean = true,
    val importAsFile: Boolean = false,
    val progress: TransferProgress = TransferProgress(TransferStage.PREPARING),
    val placed: PlacedAttachment? = null,
    val error: String? = null
)

data class PhotoAttachmentState(
    val photos: List<PendingPhoto> = emptyList(),
    val error: String? = null
) {
    val ready: Boolean get() = photos.all { it.placed != null }
}

/** Owns each immutable payload until its transfer settles, even after removal or route disposal. */
@Suppress("TooManyFunctions") // Selection, retry, cancellation, and owned-file lifecycle.
class PhotoAttachmentStore(private val source: PhotoAttachmentSource) : Closeable {
    private val mutableState = MutableStateFlow(PhotoAttachmentState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private val active = mutableSetOf<String>()

    fun independent(): PhotoAttachmentStore = PhotoAttachmentStore(source)

    @Synchronized
    fun add(uris: List<String>) = addSelection(uris, true, false)

    @Synchronized
    fun addFiles(uris: List<String>) = addSelection(uris, false, true)

    @Synchronized
    fun addMedia(uris: List<String>) = addSelection(uris, false, false)

    private fun addSelection(uris: List<String>, imageSelection: Boolean, importAsFile: Boolean) {
        if (closed) return
        val remaining = if (imageSelection) {
            MAX_PHOTOS -
                state.value.photos.count { it.imageSelection }
        } else {
            uris.size
        }
        mutableState.value =
            state.value.copy(
                error = if (uris.size >
                    remaining
                ) {
                    "Select up to $MAX_PHOTOS images."
                } else {
                    null
                }
            )
        uris.take(remaining.coerceAtLeast(0)).forEach { uri ->
            val photo =
                PendingPhoto(
                    UUID.randomUUID().toString(),
                    uri,
                    imageSelection = imageSelection,
                    importAsFile = importAsFile
                )
            mutableState.value = state.value.copy(photos = state.value.photos + photo)
            start(photo)
        }
    }

    @Synchronized
    fun retry(key: String) {
        val photo = state.value.photos.find { it.key == key } ?: return
        if (!closed && photo.error != null && key !in active) start(photo.copy(error = null))
    }

    private fun start(photo: PendingPhoto) {
        active.add(photo.key)
        update(photo.copy(progress = TransferProgress(TransferStage.PREPARING)))
        val prepared =
            photo.file?.let { CompletableFuture.completedFuture(it) }
                ?: if (photo.importAsFile) {
                    source.prepareFile(
                        photo.uri,
                        photo.key
                    )
                } else {
                    source.prepare(photo.uri, photo.key)
                }
        prepared.whenComplete { file, prepareError ->
            synchronized(this) {
                if (prepareError != null) {
                    finish(
                        photo,
                        null,
                        attachmentFailureMessage(
                            prepareError,
                            "Cannot read this attachment. Select it again or retry."
                        )
                    )
                } else if (closed || state.value.photos.none { it.key == photo.key }) {
                    active.remove(photo.key)
                    source.release(photo.key)
                    source.cleanup(file)
                } else {
                    uploadPrepared(photo, file)
                }
            }
        }
    }

    private fun uploadPrepared(photo: PendingPhoto, file: File) {
        val description = source.describe(file)
        val otherImages = state.value.photos.count { it.key != photo.key && it.imageSelection }
        if (description.image && otherImages >= MAX_PHOTOS) {
            source.cleanup(file)
            finish(
                photo.copy(file = null),
                null,
                "Select up to $MAX_PHOTOS images. Remove this attachment."
            )
        } else {
            val ready = photo.copy(
                file = file,
                description = description,
                imageSelection = description.image
            )
            update(ready)
            source.upload(file, photo.key) { progress ->
                synchronized(this) { update(ready.copy(progress = progress)) }
            }.whenComplete { placed, error ->
                synchronized(this) {
                    finish(
                        ready,
                        placed,
                        if (error !=
                            null
                        ) {
                            attachmentFailureMessage(
                                error,
                                "Upload unconfirmed. Retry uses the same attachment key."
                            )
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    private fun finish(photo: PendingPhoto, placed: PlacedAttachment?, error: String?) {
        active.remove(photo.key)
        source.release(photo.key)
        if (closed || state.value.photos.none { it.key == photo.key }) {
            photo.file?.let(source::cleanup)
        } else {
            update(
                photo.copy(
                    placed = placed,
                    error = error,
                    progress = TransferProgress(
                        if (error ==
                            null
                        ) {
                            TransferStage.COMPLETE
                        } else {
                            TransferStage.FAILED
                        }
                    )
                )
            )
        }
    }

    private fun update(photo: PendingPhoto) {
        if (!closed) {
            mutableState.value =
                state.value.copy(
                    photos = state.value.photos.map {
                        if (it.key ==
                            photo.key
                        ) {
                            photo
                        } else {
                            it
                        }
                    }
                )
        }
    }

    @Synchronized
    fun remove(key: String) {
        val photo = state.value.photos.find { it.key == key } ?: return
        mutableState.value =
            state.value.copy(photos = state.value.photos.filterNot { it.key == key })
        source.cancel(key)
        if (key !in active) photo.file?.let(source::cleanup)
    }

    @Synchronized
    fun consume(): List<PlacedAttachment> {
        check(state.value.ready)
        val placed = state.value.photos.mapNotNull { it.placed }
        state.value.photos.toList().forEach { remove(it.key) }
        return placed
    }

    @Synchronized
    override fun close() {
        state.value.photos.toList().forEach { remove(it.key) }
        closed = true
    }
}

const val MAX_PHOTOS = 5
