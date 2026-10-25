package dev.intent.app.cache

import dev.intent.app.notes.NoteEvent
import dev.intent.app.notes.NoteSource
import dev.intent.app.notes.NoteStore
import dev.intent.app.workspaces.WorkspaceSession
import dev.intent.app.workspaces.WorkspaceStore
import dev.intent.core.protocol.RpcError
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.Workspace
import dev.intent.core.protocol.session.RpcException
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStoreTest {
    @Test
    fun `cold workspace store reads cache offline then replaces it after admission`() {
        val cache = MemoryCache()
        val source = CacheWorkspaceSession()
        val store = WorkspaceStore(source, cache)
        store.start()
        assertEquals("Saved", store.state.value.workspaces.single().title)
        assertFalse(store.state.value.isLoading)
        source.admit()
        source.reads.last().completeExceptionally(IllegalStateException("Offline"))
        assertEquals("Saved", store.state.value.workspaces.single().title)
        source.admit()
        source.reads.last().complete(emptyList())
        assertTrue(store.state.value.workspaces.isEmpty())
        assertTrue(cache.workspaces().join().isEmpty())
        store.close()
    }

    @Test
    fun `cold note store serves cached body and tasks and reconnect replaces both`() {
        val cache = MemoryCache()
        val source = CacheNoteSource()
        val store = NoteStore(source, cache)
        store.open("ws", "spec")
        assertEquals("Saved", store.state.note?.content)
        assertEquals("todo", store.state.tasks.single().status)
        source.admit()
        source.notes.last().completeExceptionally(IllegalStateException("Offline"))
        assertEquals("Saved", store.state.note?.content)
        source.admit()
        source.notes.last().complete(Note("spec", "Plan", content = "Fresh"))
        assertEquals("Fresh", cache.note("ws", "spec").join()?.note?.content)
        assertTrue(cache.note("ws", "spec").join()!!.tasks.isEmpty())
        store.close()
        val offlineStore = NoteStore(CacheNoteSource(), cache)
        offlineStore.open("ws", "spec")
        assertEquals("Fresh", offlineStore.state.note?.content)
        offlineStore.close()
    }

    @Test
    fun `deleted note while offline is removed after authoritative missing response`() {
        val cache = MemoryCache()
        val source = CacheNoteSource()
        val store = NoteStore(source, cache)
        store.open("ws", "spec")
        source.admit()
        source.notes.last().completeExceptionally(
            RpcException(RpcError(-32602, "Gone", buildJsonObject { put("code", "not-found") }))
        )
        assertTrue(store.state.deleted)
        assertNull(cache.note("ws", "spec").join())
        store.close()
    }

    @Test
    fun `live deletion prevents delayed cache read from restoring old body`() {
        val cache = MemoryCache()
        val pending = CompletableFuture<CachedNote?>()
        cache.noteRead = pending
        val source = CacheNoteSource()
        val store = NoteStore(source, cache)
        store.open("ws", "spec")
        source.event(NoteEvent("note:deleted", "spec"))
        pending.complete(CachedNote(Note("spec", "Old", content = "Old"), emptyList()))
        assertTrue(store.state.deleted)
        assertNull(store.state.note)
        store.close()
    }

    @Test
    fun `late workspace cache and old server reply cannot overwrite fresh snapshot`() {
        val cache = MemoryCache()
        val pending = CompletableFuture<List<Workspace>>()
        cache.workspaceRead = pending
        val source = CacheWorkspaceSession()
        val store = WorkspaceStore(source, cache)
        store.start()
        source.admit()
        source.admit()
        source.reads[1].complete(listOf(Workspace("ws", "Fresh")))
        source.reads[0].complete(listOf(Workspace("ws", "Stale")))
        pending.complete(listOf(Workspace("ws", "Cached")))
        assertEquals("Fresh", store.state.value.workspaces.single().title)
        assertEquals("Fresh", cache.rows.single().title)
        store.close()
    }
}

private class MemoryCache : OfflineCache {
    var rows = listOf(Workspace("ws", "Saved"))
    var saved: CachedNote? =
        CachedNote(Note("spec", "Plan", content = "Saved"), listOf(NoteTaskRow(1, "Task", "todo")))
    var noteRead: CompletableFuture<CachedNote?>? = null
    var workspaceRead: CompletableFuture<List<Workspace>>? = null
    override fun workspaces() = workspaceRead ?: CompletableFuture.completedFuture(rows)
    override fun replaceWorkspaces(rows: List<Workspace>): CompletableFuture<Unit> {
        this.rows = rows
        return CompletableFuture.completedFuture(Unit)
    }
    override fun note(workspaceId: String, noteId: String) =
        noteRead ?: CompletableFuture.completedFuture(saved)
    override fun replaceNote(
        workspaceId: String,
        noteId: String,
        value: CachedNote
    ): CompletableFuture<Unit> {
        saved = value
        return CompletableFuture.completedFuture(Unit)
    }
    override fun deleteNote(workspaceId: String, noteId: String): CompletableFuture<Unit> {
        saved = null
        return CompletableFuture.completedFuture(Unit)
    }
}

private class CacheWorkspaceSession : WorkspaceSession {
    lateinit var admit: () -> Unit
    val reads = mutableListOf<CompletableFuture<List<Workspace>>>()
    override fun list() = CompletableFuture<List<Workspace>>().also(reads::add)
    override fun subscribe(
        onPush: (SubscriptionPush<Workspace>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        admit = onSubscribed
        return Closeable {}
    }
}

private class CacheNoteSource : NoteSource {
    lateinit var admit: () -> Unit
    lateinit var event: (NoteEvent) -> Unit
    val notes = mutableListOf<CompletableFuture<Note>>()
    override fun note(workspaceId: String, noteId: String) =
        CompletableFuture<Note>().also(notes::add)
    override fun tasks(workspaceId: String, noteId: String) =
        CompletableFuture.completedFuture(emptyList<NoteTaskRow>())
    override fun watch(
        workspaceId: String,
        onRefresh: () -> Unit,
        onEvent: (NoteEvent) -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        admit = onRefresh
        event = onEvent
        return Closeable {}
    }
}
