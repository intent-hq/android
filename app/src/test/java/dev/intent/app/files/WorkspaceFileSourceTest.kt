package dev.intent.app.files

import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFileSourceTest {
    @Test
    fun listsUseWorkspaceAndPathWithNoPaging() {
        val source = WorkspaceFileSource(
            WorkspaceFileRpc { method, params ->
                assertEquals("file.list", method)
                assertEquals(setOf("workspaceId", "path"), params.keys)
                assertEquals("workspace", params.getValue("workspaceId").jsonPrimitive.content)
                assertEquals("src", params.getValue("path").jsonPrimitive.content)
                CompletableFuture.completedFuture(
                    Json.parseToJsonElement("""[{"name":"a.txt","type":"file"}]""")
                )
            },
            "workspace"
        )
        assertEquals("src/a.txt", source.list("src").join().single().path)
    }

    @Test
    fun chunksUseIosWindowAndPreserveUtf8AcrossChunkBoundary() = temporary { directory ->
        val data = ("a".repeat(FILE_CHUNK_BYTES - 1) + "é").toByteArray()
        val offsets = mutableListOf<Int>()
        val source = source { params ->
            assertEquals("workspace", params.getValue("workspaceId").jsonPrimitive.content)
            assertEquals("file.txt", params.getValue("path").jsonPrimitive.content)
            assertEquals(FILE_CHUNK_BYTES, params.getValue("length").jsonPrimitive.int)
            val offset = params.getValue("offset").jsonPrimitive.int
            offsets.add(offset)
            chunk(data.copyOfRange(offset, minOf(data.size, offset + FILE_CHUNK_BYTES)), data.size)
        }
        val file = source.download("file.txt", directory, TEXT_MAX_BYTES)
        assertEquals(data.toString(Charsets.UTF_8), readWorkspaceText(file))
        assertEquals(listOf(0, FILE_CHUNK_BYTES), offsets)
    }

    @Test
    fun emptyFileAndExactLimitAreAllowed() = temporary { directory ->
        val empty = source { chunk(byteArrayOf(), 0) }.download("empty", directory, TEXT_MAX_BYTES)
        assertEquals("", readWorkspaceText(empty))
        val exact = source { chunk("abc".toByteArray(), 3) }.download("limit", directory, 3)
        assertEquals("abc", readWorkspaceText(exact))
    }

    @Test
    fun invalidChunksAndOversizeRemovePartialFiles() = temporary { directory ->
        val replies = listOf(
            chunk(byteArrayOf(), TEXT_MAX_BYTES + 1),
            chunk(byteArrayOf(), 1),
            chunk(byteArrayOf(1), 0),
            buildJsonObject {
                put("content", "!")
                put("bytesRead", 1)
                put("size", 1)
            },
            buildJsonObject {
                put("content", "YQ==")
                put("bytesRead", 2)
                put("size", 2)
            }
        )
        replies.forEach {
            assertTrue(
                runCatching {
                    source { _ -> it }.download("bad", directory, TEXT_MAX_BYTES)
                }.isFailure
            )
            assertFalse(File(directory, "bad").exists())
        }
        var calls = 0
        val changing = source {
            calls++
            chunk(byteArrayOf(1), if (calls == 1) 3 else 4)
        }
        assertTrue(
            runCatching {
                changing.download("changing", directory, TEXT_MAX_BYTES)
            }.isFailure
        )
        assertFalse(File(directory, "changing").exists())
    }

    @Test
    fun strictTextRejectsBinaryAndInvalidUtf8AndPaths() = temporary { directory ->
        listOf(byteArrayOf(0), byteArrayOf(0xC3.toByte(), 0x28)).forEach {
            val file = File(directory, "binary").apply { writeBytes(it) }
            assertTrue(runCatching { readWorkspaceText(file) }.isFailure)
        }
        assertTrue(
            runCatching {
                source {
                    error("No RPC expected")
                }.download("../outside", directory, TEXT_MAX_BYTES)
            }.isFailure
        )
        assertEquals(WorkspacePreviewKind.IMAGE, workspacePreviewKind("a.PNG"))
        assertEquals(WorkspacePreviewKind.UNSUPPORTED, workspacePreviewKind("a.pdf"))
        assertEquals(WorkspacePreviewKind.TEXT, workspacePreviewKind("Makefile"))
    }

    private fun source(reply: (JsonObject) -> JsonElement) = WorkspaceFileSource(
        WorkspaceFileRpc {
                method,
                params
            ->
            assertEquals("file.readChunk", method)
            CompletableFuture.completedFuture(reply(params))
        },
        "workspace"
    )

    private fun chunk(bytes: ByteArray, size: Int) = buildJsonObject {
        put("content", Base64.getEncoder().encodeToString(bytes))
        put("bytesRead", bytes.size)
        put("size", size)
    }

    private fun temporary(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("files-test").toFile()
        try {
            test(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
