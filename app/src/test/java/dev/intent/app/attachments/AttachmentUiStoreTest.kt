package dev.intent.app.attachments

import dev.intent.app.markdown.markdownRuns
import dev.intent.app.markdown.parseMarkdown
import dev.intent.core.protocol.attachments.PlacedAttachment
import dev.intent.core.protocol.attachments.TransferProgress
import java.io.File
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentUiStoreTest {
    @Test
    fun questionSelectionIsIndependentAndLatePreparationIsDeleted() {
        val prepared = CompletableFuture<File>()
        val source = object : PhotoAttachmentSource {
            override fun prepare(uri: String) = prepared
            override fun upload(
                file: File,
                key: String,
                progress: (TransferProgress) -> Unit
            ): CompletableFuture<PlacedAttachment> = error("Removed import must not upload")
        }
        val composer = PhotoAttachmentStore(source)
        val question = composer.independent()
        question.add(listOf("file"))
        assertTrue(composer.state.value.photos.isEmpty())
        question.close()
        val file = File.createTempFile("import", ".tmp")
        prepared.complete(file)
        assertFalse(file.exists())
        assertTrue(question.state.value.photos.isEmpty())
    }

    @Test
    fun uploadFailureRetainsPayloadAndKeyForExplicitRetry() {
        val source = FakePhotos()
        val store = PhotoAttachmentStore(source)
        store.add(listOf("photo"))
        assertFalse(store.state.value.ready)
        source.results[0].completeExceptionally(IllegalStateException("lost reply"))
        val failed = store.state.value.photos.single()
        assertNotNull(failed.error)
        store.retry(failed.key)
        assertEquals(source.keys[0], source.keys[1])
        assertEquals(1, source.prepares)
        source.results[1].complete(placed())
        assertTrue(store.state.value.ready)
        assertEquals(listOf(placed()), store.consume())
        assertFalse(source.file.exists())
        assertTrue(store.state.value.photos.isEmpty())
    }

    @Test
    fun removalAndCloseWaitForActiveUploadBeforeDeletingPayload() {
        val source = FakePhotos()
        val store = PhotoAttachmentStore(source)
        store.add(listOf("photo"))
        store.close()
        assertEquals(source.keys, source.cancelled)
        assertTrue(source.file.exists())
        source.results.single().complete(placed())
        assertFalse(source.file.exists())
        assertTrue(store.state.value.photos.isEmpty())
    }

    @Test
    fun pickerFallbackCannotExceedPhotoLimit() {
        val source = FakePhotos()
        val store = PhotoAttachmentStore(source)
        store.add(List(MAX_PHOTOS + 2) { "$it" })
        assertEquals(MAX_PHOTOS, store.state.value.photos.size)
        store.close()
        source.results.forEach { it.complete(placed()) }
    }

    @Test
    fun renderLoadingFailureRetryAndCloseIgnoreLateResult() {
        val results = mutableListOf<CompletableFuture<File>>()
        val store =
            MediaLoadStore(
                MediaSource {
                    CompletableFuture<File>().also { results.add(it) }
                },
                MediaReference("image", true, "id")
            )
        store.load()
        store.load()
        assertEquals(1, results.size)
        assertTrue(store.state.value.loading)
        results[0].completeExceptionally(IllegalStateException("offline"))
        assertFalse(store.state.value.loading)
        assertNotNull(store.state.value.error)
        store.load()
        results[1].complete(File("cached"))
        assertEquals(File("cached"), store.state.value.file)
        assertNull(store.state.value.error)
        store.load()
        store.close()
        results[2].complete(File("late"))
        assertNull(store.state.value.file)
    }

    @Test
    fun noteLinksDecodePathsAndRejectTraversalOrForeignHosts() {
        val parts =
            markdownRuns(
                parseMarkdown(
                    "Before ![Photo](intent://local/file/.intent/attachments/a%20b.jpg) after"
                )
            )
        assertEquals(3, parts.size)
        assertEquals(".intent/attachments/a b.jpg", parts[1].media?.path)
        assertTrue(parts[1].media?.image == true)
        assertNull(noteMediaReference("intent://local/file/%2e%2e/private", "bad", true))
        assertNull(noteMediaReference("https://example.com/image.jpg", "remote", true))
        assertNull(noteMediaReference("intent://other/file/a", "bad", true))
        assertFalse(safeWorkspacePath("a\\b"))
    }

    private fun placed() =
        PlacedAttachment("id", "photo.jpg", ".intent/attachments/photo.jpg", 10, "image/jpeg")

    private class FakePhotos : PhotoAttachmentSource {
        val file = File.createTempFile("photo", ".jpg").also { it.writeText("photo") }
        val keys = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        override fun cancel(key: String) {
            cancelled.add(key)
        }
        val results = mutableListOf<CompletableFuture<PlacedAttachment>>()
        var prepares = 0
        override fun prepare(uri: String): CompletableFuture<File> {
            prepares++
            return CompletableFuture.completedFuture(file)
        }
        override fun upload(
            file: File,
            key: String,
            progress: (TransferProgress) -> Unit
        ): CompletableFuture<PlacedAttachment> {
            keys.add(key)
            return CompletableFuture<PlacedAttachment>().also { results.add(it) }
        }
    }
}
