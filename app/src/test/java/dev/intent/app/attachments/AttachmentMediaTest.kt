package dev.intent.app.attachments

import dev.intent.core.protocol.attachments.AttachmentRpc
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttachmentMediaTest {
    @Test
    fun pathDownloadsUseWorkspaceScopeAndCacheOnlyCompleteBytes() {
        val directory = Files.createTempDirectory("media").toFile()
        try {
            var calls = 0
            var valid = false
            val rpc = AttachmentRpc { method, params ->
                calls++
                assertEquals("file.readChunk", method)
                assertEquals("ws", params.getValue("workspaceId").jsonPrimitive.content)
                CompletableFuture.completedFuture(
                    buildJsonObject {
                        put("content", Base64.getEncoder().encodeToString("photo".toByteArray()))
                        put("bytesRead", if (valid) 5 else 6)
                        put("size", 5)
                    }
                )
            }
            val source = AttachmentMedia(rpc, "ws", directory, "principal-a")
            val reference = MediaReference("photo", true, path = ".intent/attachments/photo.jpg")
            assertFalse(runCatching { source.load(reference).join() }.isSuccess)
            valid = true
            val first = source.load(reference).join()
            assertEquals("photo", first.readText())
            assertEquals(first, source.load(reference).join())
            assertEquals(2, calls)
            val other = AttachmentMedia(rpc, "ws", directory, "principal-b").load(reference).join()
            assertNotEquals(first, other)
            assertEquals(3, calls)
            assertFalse(directory.walkTopDown().any { it.extension == "part" })
        } finally {
            directory.deleteRecursively()
        }
    }
}
