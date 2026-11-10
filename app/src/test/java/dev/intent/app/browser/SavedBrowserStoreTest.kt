package dev.intent.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedBrowserStoreTest {
    @Test
    fun `blank host scope uses fresh memory without opening persistent storage`() {
        val first =
            browserPersistence("", "workspace") { error("Must not open persistent storage") }
        first.save(listOf(SavedBrowser("one", "Page", "https://example.test")))
        val second =
            browserPersistence(" ", "workspace") { error("Must not open persistent storage") }
        assertTrue(second.load().isEmpty())
        val persistent = MemoryBrowsers()
        assertEquals(persistent, browserPersistence("host", "workspace") { persistent })
    }

    @Test
    fun `saved URLs and fallback titles omit query and fragment credentials`() {
        val persistence = MemoryBrowsers()
        val store = SavedBrowserStore(persistence).apply { load() }
        store.savePage(
            null,
            "https://example.test/a%20b?token=private#access_token=private",
            "Private token title"
        )
        assertEquals("https://example.test/a%20b", persistence.rows.single().url)
        assertEquals("https://example.test/a%20b", persistence.rows.single().title)
        store.savePage(null, "https://example.test/a%20b?token=replaced", null)
        assertEquals(1, persistence.rows.size)
    }

    @Test
    fun `loading rewrites old raw URLs and removes credential URLs`() {
        val persistence = MemoryBrowsers().apply {
            rows = listOf(
                SavedBrowser(
                    "one",
                    "https://example.test?token=private",
                    "https://example.test?token=private"
                ),
                SavedBrowser("two", "Invalid", "https://user:password@example.test")
            )
        }
        SavedBrowserStore(persistence).load()
        assertEquals(
            listOf(SavedBrowser("one", "https://example.test", "https://example.test")),
            persistence.rows
        )
    }

    @Test
    fun `page title and address survive returning to the list and reopening the store`() {
        val persistence = MemoryBrowsers()
        val store = SavedBrowserStore(persistence)
        store.load()
        val id = store.savePage(null, "http://localhost:3000", "Preview")
        store.savePage(id, "http://localhost:3000/settings", "Settings")
        val reopened = SavedBrowserStore(persistence).apply { load() }
        assertEquals(
            listOf(SavedBrowser(id!!, "Settings", "http://localhost:3000/settings")),
            reopened.state.value.browsers
        )
        reopened.remove(id)
        assertTrue(SavedBrowserStore(persistence).apply { load() }.state.value.browsers.isEmpty())
    }

    @Test
    fun `opening the same address reuses the saved entry`() {
        val store = SavedBrowserStore(MemoryBrowsers()).apply { load() }
        val id = store.savePage(null, "https://example.test", "First title")
        assertEquals(id, store.savePage(null, "https://example.test", "New title"))
        assertEquals(1, store.state.value.browsers.size)
        assertEquals("New title", store.state.value.browsers.single().title)
    }

    @Test
    fun `invalid URLs cannot persist credentials or local files`() {
        val store = SavedBrowserStore(MemoryBrowsers()).apply { load() }
        listOf(
            "file:///private",
            "javascript:alert(1)",
            "https://user:password@example.test"
        ).forEach {
            assertNull(store.savePage(null, it, "Invalid"))
        }
        assertTrue(store.state.value.browsers.isEmpty())
        assertNotNull(store.state.value.error)
    }

    @Test
    fun `failed removal retains the row and failed loading cannot overwrite saved data`() {
        val persistence = MemoryBrowsers()
        val store = SavedBrowserStore(persistence).apply { load() }
        val id = store.savePage(null, "https://example.test", null)!!
        persistence.failSave = true
        store.remove(id)
        assertEquals(id, store.state.value.browsers.single().id)
        assertNotNull(store.state.value.error)
        persistence.failLoad = true
        persistence.failSave = false
        val unread = SavedBrowserStore(persistence).apply { load() }
        assertNull(unread.savePage(null, "https://another.test", null))
        assertEquals(id, persistence.rows.single().id)
    }

    @Test
    fun `saved host and workspace IDs have unambiguous storage keys`() {
        assertNotEquals(browserStorageKey("a", "bc"), browserStorageKey("ab", "c"))
        assertNotEquals(
            browserStorageKey("host-a", "workspace"),
            browserStorageKey("host-b", "workspace")
        )
        assertNotEquals(browserStorageKey("host-a", "one"), browserStorageKey("host-a", "two"))
        assertEquals(browserStorageKey("host-a", "one"), browserStorageKey("host-a", "one"))
    }

    private class MemoryBrowsers : SavedBrowserPersistence {
        var rows: List<SavedBrowser> = emptyList()
        var failSave = false
        var failLoad = false
        override fun load(): List<SavedBrowser> {
            check(!failLoad)
            return rows
        }
        override fun save(browsers: List<SavedBrowser>) {
            check(!failSave)
            rows = browsers
        }
    }
}
