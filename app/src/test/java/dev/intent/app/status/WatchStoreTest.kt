package dev.intent.app.status

import java.io.Closeable
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStoreTest {
    @Test fun triggerPreviewIsBoundedWithoutSplittingUnicode() {
        val full = JsonObject(
            script() + mapOf(
                "scriptId" to JsonPrimitive("script"),
                "scriptName" to JsonPrimitive("Build"),
                "mode" to JsonPrimitive("command"),
                "createdAt" to JsonPrimitive("bad date"),
                "expiresAt" to JsonPrimitive("bad date"),
                "outputPattern" to JsonPrimitive("😀".repeat(50))
            )
        )
        val summary = scriptSummary(full)
        assertFalse("outputPattern" in summary)
        assertEquals("😀".repeat(40), summary.text("outputPatternPreview"))
        assertEquals("true", summary.text("outputPatternTruncated"))
        assertEquals(
            "Expiry unavailable",
            WatchTime.expiry(summary.text("expiresAt"), Instant.now())
        )
    }

    @Test fun timePresentationDoesNotClaimRunCompletion() {
        val now = Instant.parse("2026-10-27T00:00:00Z")
        assertEquals("Watching for 1m 0s", WatchTime.elapsed("2026-10-26T23:59:00Z", now))
        assertEquals("Watch expires in 1m 0s", WatchTime.expiry("2026-10-27T00:01:00Z", now))
        assertEquals(WatchTime.DEADLINE, WatchTime.expiry("2026-10-26T23:59:59Z", now))
        assertEquals("Expiry unavailable", WatchTime.expiry("bad", now))
        assertEquals(0.5f, WatchTime.fraction("2026-10-27T00:01:00Z", 120000, now))
        assertEquals(0f, WatchTime.fraction("2026-10-26T23:59:00Z", 120000, now))
        assertEquals(1f, WatchTime.fraction("2026-10-27T01:00:00Z", 120000, now))
    }

    @Test fun terminalEventFencesLatePageAndDetail() {
        val source = FakeWatches()
        val store = WatchStore(source)
        store.start()
        source.ready.getValue(WatchFamily.HOOK)()
        source.ready.getValue(WatchFamily.SCRIPT)()
        store.select(script())
        source.events.getValue(WatchFamily.SCRIPT)(event("scriptMonitor:expired", script()))
        source.reads.getValue(WatchFamily.SCRIPT).last().complete(WatchPage(listOf(script())))
        source.details.last().complete(script())
        assertTrue(store.state.value.rows(WatchFamily.SCRIPT, "a").isEmpty())
        assertTrue(store.state.value.selected!!.unavailable)
        assertNull(store.state.value.selected!!.detail)
    }

    @Test fun moreIsExplicitAndOldReadsCannotReplaceReconnect() {
        val source = FakeWatches()
        val store = WatchStore(source)
        store.start()
        source.ready.getValue(WatchFamily.HOOK)()
        source.ready.getValue(WatchFamily.SCRIPT)()
        val first = source.reads.getValue(WatchFamily.SCRIPT).last()
        source.ready.getValue(WatchFamily.SCRIPT)()
        source.reads.getValue(
            WatchFamily.SCRIPT
        ).last().complete(WatchPage(listOf(script()), "next"))
        first.complete(WatchPage())
        assertEquals(1, store.state.value.rows(WatchFamily.SCRIPT, "a").size)
        assertEquals(2, source.reads.getValue(WatchFamily.SCRIPT).size)
        store.refresh(WatchFamily.SCRIPT, more = true)
        assertEquals("next", source.cursors.last())
        source.reads.getValue(WatchFamily.SCRIPT).last().complete(WatchPage(listOf(script("two"))))
        assertEquals(2, store.state.value.rows(WatchFamily.SCRIPT, "a").size)
        store.close()
        assertEquals(3, source.closed)
    }

    @Test fun oldRunTerminalDoesNotRemoveNewRunAndAgentFilteringIsExact() {
        val newer = JsonObject(script() + ("runId" to JsonPrimitive("new")))
        val rows =
            foldWatch(WatchFamily.SCRIPT, listOf(newer), event("scriptMonitor:completed", script()))
        assertEquals(listOf(newer), rows)
        val state = WatchState(mapOf(WatchFamily.SCRIPT to WatchBucket(WatchPage(rows))))
        assertTrue(state.isWaiting("a"))
        assertFalse(state.isWaiting("other"))
    }

    @Test fun unsupportedHostsNeverReadScripts() {
        val source = FakeWatches(false)
        val store = WatchStore(source)
        store.start()
        source.ready.getValue(WatchFamily.HOOK)()
        assertFalse(WatchFamily.SCRIPT in source.ready)
        store.refresh(WatchFamily.SCRIPT)
        assertFalse(WatchFamily.SCRIPT in source.reads)
    }

    @Test fun failuresKeepRowsAndDismissFencesDetails() {
        val source = FakeWatches()
        val store = WatchStore(source)
        store.start()
        source.ready.getValue(WatchFamily.HOOK)()
        source.ready.getValue(WatchFamily.SCRIPT)()
        source.reads.getValue(WatchFamily.SCRIPT).last().complete(WatchPage(listOf(script())))
        store.refresh(WatchFamily.SCRIPT)
        source.reads.getValue(
            WatchFamily.SCRIPT
        ).last().completeExceptionally(IllegalStateException())
        assertTrue(store.state.value.isWaiting("a"))
        assertNotNull(store.state.value.buckets[WatchFamily.SCRIPT]!!.error)
        store.select(script())
        store.dismiss()
        source.details.last().complete(script())
        assertNull(store.state.value.selected)
    }
}

private fun script(id: String = "watch") = JsonObject(
    mapOf(
        "monitorId" to JsonPrimitive(id),
        "runId" to JsonPrimitive("run"),
        "agentId" to JsonPrimitive("a"),
        "workspaceId" to JsonPrimitive("ws"),
        "state" to JsonPrimitive("active")
    )
)
private fun event(type: String, row: JsonObject) = JsonObject(
    mapOf(
        "type" to JsonPrimitive(type),
        "data" to JsonObject(mapOf("monitor" to row))
    )
)
private class FakeWatches(override val scriptsSupported: Boolean = true) : WatchSource {
    val ready = mutableMapOf<WatchFamily, () -> Unit>()
    val events = mutableMapOf<WatchFamily, (JsonObject) -> Unit>()
    val reads = mutableMapOf<WatchFamily, MutableList<CompletableFuture<WatchPage>>>()
    val details = mutableListOf<CompletableFuture<JsonObject>>()
    val cursors = mutableListOf<String?>()
    var closed = 0
    override fun list(family: WatchFamily, cursor: String?): CompletableFuture<WatchPage> {
        cursors.add(cursor)
        return CompletableFuture<WatchPage>().also {
            reads.getOrPut(family) { mutableListOf() }.add(it)
        }
    }
    override fun detail(id: String) = CompletableFuture<JsonObject>().also { details.add(it) }
    override fun subscribe(
        family: WatchFamily,
        event: (JsonObject) -> Unit,
        ready: () -> Unit,
        error: (Throwable) -> Unit
    ): Closeable {
        this.ready[family] = ready
        events[family] = event
        return Closeable { closed++ }
    }
}
