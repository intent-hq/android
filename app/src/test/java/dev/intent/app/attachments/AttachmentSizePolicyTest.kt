package dev.intent.app.attachments

import java.io.File
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSizePolicyTest {
    @Test
    fun exactVideoBoundaryIsAllowedAndNextByteIsRejected() {
        listOf(
            AttachmentDescription("clip.bin", "VIDEO/MP4"),
            AttachmentDescription("clip.MOV", "application/octet-stream"),
            AttachmentDescription("clip.webm", "application/octet-stream"),
            AttachmentDescription("clip.m4v", "application/octet-stream")
        ).forEach { description ->
            assertTrue(description.isVideo)
            description.validateSize(MAX_VIDEO_BYTES)
            assertEquals(
                VIDEO_TOO_LARGE,
                assertThrows(VideoAttachmentTooLarge::class.java) {
                    description.validateSize(MAX_VIDEO_BYTES + 1)
                }.message
            )
        }
    }

    @Test
    fun nonVideoKeepsOneGiBLimit() {
        val file = AttachmentDescription("data.zip", "application/zip")
        file.validateSize(201L * 1024 * 1024)
        file.validateSize(MAX_FILE_BYTES)
        assertThrows(IllegalArgumentException::class.java) { file.validateSize(MAX_FILE_BYTES + 1) }
    }

    @Test
    fun mediaAndDocumentPreparationShowExactRejectionWithoutUpload() {
        val source = object : PhotoAttachmentSource {
            override fun prepare(uri: String): CompletableFuture<File> =
                CompletableFuture.failedFuture(VideoAttachmentTooLarge())
            override fun upload(
                file: File,
                key: String,
                progress: (dev.intent.core.protocol.attachments.TransferProgress) -> Unit
            ): CompletableFuture<dev.intent.core.protocol.attachments.PlacedAttachment> =
                error("Must not upload")
        }
        PhotoAttachmentStore(source).use { store ->
            store.addMedia(listOf("video"))
            store.addFiles(listOf("document"))
            assertEquals(
                listOf(VIDEO_TOO_LARGE, VIDEO_TOO_LARGE),
                store.state.value.photos.map {
                    it.error
                }
            )
        }
    }
}
