package dev.intent.app.status

import dev.intent.core.protocol.models.Agent
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentWatchStoreTest {
    private val watched = AgentWatches(listOf(WatchedAgent(Agent("child", "Worker"))), total = 1)

    @Test fun admissionEventsAndReconnectRefreshWithoutStaleReads() {
        val source = FakeAgentWatchSource()
        val store = AgentWatchStore(source, "parent")
        store.start()
        assertTrue(source.reads.isEmpty())
        source.ready()
        source.changed("other")
        assertEquals(1, source.reads.size)
        source.changed("parent")
        source.reads[0].complete(watched)
        assertTrue(store.state.value.rows.isEmpty())
        source.reads[1].complete(watched)
        assertEquals(watched, store.state.value)
        source.changed("child")
        source.reads[2].complete(AgentWatches())
        assertTrue(store.state.value.rows.isEmpty())
        source.ready()
        source.reads[3].complete(watched)
        assertEquals(watched, store.state.value)
        store.close()
        assertTrue(source.closed)
        source.ready()
        assertEquals(4, source.reads.size)
    }

    @Test fun failedFeedInvalidatesOldReadsAndRetryClearsError() {
        val source = FakeAgentWatchSource()
        val store = AgentWatchStore(source, "parent")
        store.start()
        source.ready()
        source.error(IllegalStateException("offline"))
        assertNotNull(store.state.value.error)
        source.ready()
        source.changed("parent")
        source.reads[0].complete(watched)
        assertTrue(store.state.value.rows.isEmpty())
        assertEquals(2, source.reads.size)
        source.reads[1].complete(watched)
        source.reads[2].complete(AgentWatches())
        assertNull(store.state.value.error)
        store.refresh()
        store.close()
        source.reads[3].complete(watched)
        assertTrue(store.state.value.rows.isEmpty())
    }
}

private class FakeAgentWatchSource : AgentWatchSource {
    val reads = mutableListOf<CompletableFuture<AgentWatches>>()
    lateinit var changed: (String) -> Unit
    lateinit var ready: () -> Unit
    lateinit var error: (Throwable) -> Unit
    var closed = false
    override fun read() = CompletableFuture<AgentWatches>().also(reads::add)
    override fun subscribe(
        changed: (String) -> Unit,
        ready: () -> Unit,
        error: (Throwable) -> Unit
    ): Closeable {
        this.changed = changed
        this.ready = ready
        this.error = error
        return Closeable { closed = true }
    }
}
