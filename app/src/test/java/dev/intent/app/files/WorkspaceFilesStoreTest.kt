package dev.intent.app.files

import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceFilesStoreTest {
    @Test
    fun listingSortsDirectoriesAndNaturalNamesAndOnlyHidesGit() {
        val entries =
            parseFileList(
                Json.parseToJsonElement(
                    """[
            {"name":"file10.txt","type":"file"}, {"name":"file2.txt","type":"file"},
            {"name":".git","type":"directory"}, {"name":".env","type":"file"},
            {"name":"src","type":"directory"}, {"name":".intent","type":"directory"}
        ]"""
                ),
                "."
            )
        assertEquals(
            listOf(".intent", "src", ".env", "file2.txt", "file10.txt"),
            entries.map {
                it.path
            }
        )
        assertEquals(
            "src/file",
            parseFileList(
                Json.parseToJsonElement("""[{"name":"file","type":"file"}]"""),
                "src"
            ).single().path
        )
    }

    @Test
    fun malformedNamesTypesAndDuplicateNamesRejectWholeListing() {
        listOf("..", "a/b", "a\\\\b", "").forEach { name ->
            assertTrue(
                runCatching {
                    parseFileList(
                        Json.parseToJsonElement("""[{"name":"$name","type":"file"}]"""),
                        "."
                    )
                }.isFailure
            )
        }
        listOf(
            """[{"name":"a","type":"link"}]""",
            """[{"name":"a","type":"file"},{"name":"a","type":"file"}]""",
            "{}"
        ).forEach {
            assertTrue(runCatching { parseFileList(Json.parseToJsonElement(it), ".") }.isFailure)
        }
    }

    @Test
    fun lazyExpansionCachesChildrenAndRefreshesOnlyVisibleExpandedBranches() {
        val calls = mutableListOf<String>()
        val listing = mapOf(
            "." to listOf(folder("src")),
            "src" to listOf(folder("src/nested")),
            "src/nested" to listOf(file("src/nested/a"))
        )
        WorkspaceFilesStore { path ->
            calls.add(path)
            CompletableFuture.completedFuture(listing.getValue(path))
        }.use { store ->
            store.refresh()
            assertEquals(listOf("."), calls)
            store.toggle("src")
            store.toggle("src/nested")
            assertEquals(listOf(0, 1, 2), store.state.value.rows.map { it.depth })
            store.toggle("src")
            store.refresh()
            assertEquals(listOf(".", "src", "src/nested", "."), calls)
            store.toggle("src")
            assertEquals(4, calls.size)
            store.refresh()
            assertEquals(listOf(".", "src", "src/nested"), calls.takeLast(3))
        }
    }

    @Test
    fun failedRefreshRetainsRowsAndRetryPrunesRemovedFolders() {
        var reply = CompletableFuture.completedFuture(listOf(folder("src")))
        WorkspaceFilesStore { reply }.use { store ->
            store.refresh()
            reply = CompletableFuture.completedFuture(listOf(file("src/a")))
            store.toggle("src")
            reply = CompletableFuture.failedFuture(IllegalStateException("offline"))
            store.refresh()
            assertEquals(listOf("src", "src/a"), store.state.value.rows.map { it.entry.path })
            assertTrue("." in store.state.value.errors)
            assertTrue(store.state.value.loading.isEmpty())
            reply = CompletableFuture.completedFuture(emptyList())
            store.retry(".")
            assertTrue(store.state.value.rows.isEmpty())
            assertTrue(store.state.value.expanded.isEmpty())
            assertFalse("src" in store.state.value.entries)
        }
    }

    @Test
    fun refreshAndCloseIgnoreOldReplies() {
        val replies = mutableListOf<CompletableFuture<List<WorkspaceFileEntry>>>()
        val store =
            WorkspaceFilesStore { CompletableFuture<List<WorkspaceFileEntry>>().also(replies::add) }
        store.refresh()
        store.refresh()
        replies[1].complete(listOf(file("new")))
        replies[0].complete(listOf(file("old")))
        assertEquals("new", store.state.value.rows.single().entry.path)
        store.refresh()
        store.close()
        replies[2].complete(emptyList())
        assertEquals("new", store.state.value.rows.single().entry.path)
    }

    private fun folder(path: String) = WorkspaceFileEntry(path, path.substringAfterLast('/'), true)
    private fun file(path: String) = WorkspaceFileEntry(path, path.substringAfterLast('/'), false)
}
