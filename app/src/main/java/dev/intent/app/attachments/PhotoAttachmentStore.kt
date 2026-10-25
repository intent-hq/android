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
    fun upload(
        file: File,
        key: String,
        progress: (TransferProgress) -> Unit
    ): CompletableFuture<PlacedAttachment>
}

data class PendingPhoto(
    val key: String,
    val uri: String,
    val file: File? = null,
    val progress: TransferProgress = TransferProgress(TransferStage.PREPARING),
    val placed: PlacedAttachment? = null,
    val error: String? = null
)

data class PhotoAttachmentState(val photos: List<PendingPhoto> = emptyList()) {
    val ready: Boolean get() = photos.all { it.placed != null }
}

/** Owns each immutable payload until its transfer settles, even after removal or route disposal. */
class PhotoAttachmentStore(private val source: PhotoAttachmentSource) : Closeable {
    private val mutableState = MutableStateFlow(PhotoAttachmentState())
    val state = mutableState.asStateFlow()
    private var closed = false
    private val active = mutableSetOf<String>()

    @Synchronized
    fun add(uris: List<String>) {
        if (closed) return
        uris.take(MAX_PHOTOS - state.value.photos.size).forEach { uri ->
            val photo = PendingPhoto(UUID.randomUUID().toString(), uri)
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
            photo.file?.let { CompletableFuture.completedFuture(it) } ?: source.prepare(photo.uri)
        prepared.whenComplete { file, prepareError ->
            synchronized(this) {
                if (prepareError != null) {
                    finish(photo, null, "Cannot read this photo. Select it again or retry.")
                } else if (closed || state.value.photos.none { it.key == photo.key }) {
                    active.remove(photo.key)
                    file.delete()
                } else {
                    val ready = photo.copy(file = file)
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
                                    "Upload unconfirmed. Retry uses the same attachment key."
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun finish(photo: PendingPhoto, placed: PlacedAttachment?, error: String?) {
        active.remove(photo.key)
        if (closed || state.value.photos.none { it.key == photo.key }) {
            photo.file?.delete()
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
        if (key !in active) photo.file?.delete()
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

const val MAX_PHOTOS = 4
