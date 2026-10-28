package dev.intent.app.browser

import dev.intent.core.protocol.browser.BrowserTunnel
import dev.intent.core.protocol.browser.TunnelTransport
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStoreTest {
    @Test
    fun `stale session result closes its tunnel without publishing a URL`() {
        val pending = CompletableFuture<BrowserTunnel>()
        val released = LinkedBlockingQueue<BrowserTunnel>()
        val store = BrowserStore({ pending }, {
            it.close()
            released.add(it)
        })
        store.open("http://daemon.localhost:3000")
        store.close()
        val tunnel = tunnel()
        pending.complete(tunnel)
        assertEquals(tunnel, released.poll(5, TimeUnit.SECONDS))
        assertNull(store.state.value.url)
    }

    @Test
    fun `invalid URL never opens a tunnel and shows retry state`() {
        var opened = false
        BrowserStore({
            opened = true
            CompletableFuture()
        }, { it.close() }).use { store ->
            store.open("javascript:alert(1)")
            assertTrue(!opened)
            assertNotNull(store.state.value.error)
            assertTrue(!store.state.value.loading)
        }
    }

    @Test
    fun `stale WebView callbacks cannot clear a newer failure`() {
        BrowserStore({ CompletableFuture() }, { it.close() }).use { store ->
            store.open("http://daemon.localhost:3000")
            val old = store.state.value.revision
            store.open("file:///private")
            store.loaded(old)
            assertNotNull(store.state.value.error)
        }
    }

    @Test
    fun `failed page stays failed after finish callback and retry clears error`() {
        BrowserStore({ CompletableFuture() }, { it.close() }).use { store ->
            store.open("http://daemon.localhost:3000")
            val revision = store.state.value.revision
            store.failed(revision)
            store.loaded(revision)
            assertNotNull(store.state.value.error)
            assertNull(store.state.value.url)
            store.open("http://daemon.localhost:3000")
            assertNull(store.state.value.error)
            assertTrue(store.state.value.loading)
            store.failed(revision)
            assertNull(store.state.value.error)
        }
    }

    @Test
    fun `unavailable tunnel gives host update instructions without publishing localhost`() {
        val pending = CompletableFuture<BrowserTunnel>()
        BrowserStore({ pending }, { it.close() }).use { store ->
            store.open("http://localhost:4321/preview")
            pending.completeExceptionally(java.io.IOException("upgrade rejected"))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (store.state.value.loading && System.nanoTime() < deadline) Thread.yield()
            assertTrue(store.state.value.error.orEmpty().contains("update Intent on the host"))
            assertNull(store.state.value.url)
            assertEquals("http://localhost:4321/preview", store.state.value.requestedUrl)
        }
    }

    private fun tunnel() = BrowserTunnel({ listener ->
        object : TunnelTransport {
            override fun connect() = listener.opened()
            override fun send(bytes: ByteArray) = true
            override fun close() = Unit
        }
    }, false)
}
