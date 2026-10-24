package dev.intent.core.protocol.attachments

import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentServiceTest {
    private val direct = Executor { it.run() }
    private val payload = "hello".toByteArray()
    private val placement = """{"ok":true,"attachmentId":"a","fileName":"x.png",
        "path":".intent/attachments/x.png","size":5,"mimeType":"image/png"}"""
    private val info = placement.dropLast(1) + ",\"exists\":true}"

    @Test
    fun `small upload preserves key mime and placement references`() {
        val rpc = Fake { method, params ->
            assertEquals("file.placeAttachment", method)
            assertEquals("w", params.text("workspaceId"))
            assertEquals("key", params.text("idempotencyKey"))
            assertEquals("image/png", params.text("mimeType"))
            assertArrayEquals(payload, Base64.getDecoder().decode(params.text("data")))
            placement
        }
        val result = AttachmentUploadService(rpc, "w", direct)
            .placeData(payload, "x.png", "image/png", "key").join()
        assertEquals("a", result.messageBlock(true).text("attachmentId"))
        assertEquals("file", result.messageBlock().text("type"))
        assertEquals(
            "![x.png](intent://local/file/.intent/attachments/x.png)",
            result.noteMarkdown(true)
        )
        assertEquals(
            "[a\\] b](intent://local/file/.intent/attachments/a%20%23.png)",
            result.copy(fileName = "a] b", path = ".intent/attachments/a #.png").noteMarkdown()
        )
    }

    @Test
    fun `chunk upload streams negotiated slices and commits in order`() {
        val directory = Files.createTempDirectory("upload-test").toFile()
        try {
            val file = directory.resolve("x.png").apply { writeBytes(payload) }
            val chunks = mutableListOf<Byte>()
            val rpc = Fake { method, params ->
                assertEquals("w", params.text("workspaceId"))
                when (method) {
                    "file.attachmentUpload.begin" -> {
                        assertEquals(
                            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                            params.text("sha256")
                        )
                        """{"uploadId":"u","maxChunkBytes":2}"""
                    }
                    "file.attachmentUpload.chunk" -> {
                        assertEquals((chunks.size / 2).toString(), params.text("seq"))
                        chunks.addAll(Base64.getDecoder().decode(params.text("data")).toList())
                        "{}"
                    }
                    "file.attachmentUpload.commit" -> placement
                    else -> error(method)
                }
            }
            val progress = mutableListOf<TransferProgress>()
            AttachmentUploadService(
                rpc,
                "w",
                direct
            ).placeFile(file, forceChunked = true, onProgress = progress::add).join()
            assertArrayEquals(payload, chunks.toByteArray())
            assertEquals(TransferStage.COMPLETE, progress.last().stage)
            assertEquals(3, progress.count { it.stage == TransferStage.UPLOADING })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `failed staged upload aborts and explicit retry works`() {
        val file = Files.createTempFile("upload", ".bin").toFile().apply { writeBytes(payload) }
        try {
            var fail = true
            val rpc = Fake { method, _ ->
                when (method) {
                    "file.attachmentUpload.begin" -> """{"uploadId":"u","maxChunkBytes":16}"""
                    "file.attachmentUpload.chunk" -> if (fail) error("disconnected") else "{}"
                    "file.attachmentUpload.commit" -> placement
                    else -> "{}"
                }
            }
            val service = AttachmentUploadService(rpc, "w", direct)
            assertThrows(Exception::class.java) {
                service.placeFile(file, forceChunked = true).join()
            }
            assertEquals("file.attachmentUpload.abort", rpc.methods.last())
            fail = false
            assertEquals("a", service.placeFile(file, forceChunked = true).join().attachmentId)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `download cache hit is scoped and failures can retry`() {
        val directory = Files.createTempDirectory("download-test").toFile()
        try {
            var fail = true
            val rpc = Fake { method, _ ->
                when (method) {
                    "file.getAttachmentInfo" -> info
                    else -> if (fail) {
                        """{"content":"","bytesRead":0,"size":5}"""
                    } else {
                        """{"content":"aGVsbG8=","bytesRead":5,"size":5}"""
                    }
                }
            }
            val service = AttachmentDownloadService(rpc, "w", directory, "principal", direct)
            assertThrows(Exception::class.java) { service.download("a").join() }
            assertTrue(directory.walk().none { it.extension == "part" })
            fail = false
            assertArrayEquals(payload, service.download("a").join().readBytes())
            val count = rpc.methods.size
            service.download("a").join()
            assertEquals(count, rpc.methods.size)
            AttachmentDownloadService(
                rpc,
                "w",
                directory,
                "other-principal",
                direct
            ).download("a").join()
            assertEquals(count + 2, rpc.methods.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `lost commit recovery is lookup only`() {
        val rpc = Fake { method, params ->
            assertEquals("file.getAttachmentInfo", method)
            assertEquals("key", params.text("idempotencyKey"))
            assertTrue(!params.containsKey("attachmentId"))
            info
        }
        assertEquals(
            "a",
            AttachmentUploadService(rpc, "w", direct).recover("key").join().attachmentId
        )
        assertEquals(1, rpc.methods.size)
    }

    private class Fake(val respond: (String, JsonObject) -> String) : AttachmentRpc {
        val methods = mutableListOf<String>()
        override fun request(method: String, params: JsonObject): CompletableFuture<JsonObject> {
            methods.add(method)
            return try {
                CompletableFuture.completedFuture(
                    Json.parseToJsonElement(respond(method, params)) as JsonObject
                )
            } catch (failure: Exception) {
                CompletableFuture<JsonObject>().also { it.completeExceptionally(failure) }
            }
        }
    }
}

private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
