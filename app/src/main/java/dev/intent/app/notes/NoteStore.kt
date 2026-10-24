package dev.intent.app.notes

import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import java.io.Closeable
import java.util.concurrent.CompletableFuture

/** Read-only seam for the session and the future offline mirror. */
interface NoteSource {
    fun note(workspaceId: String, noteId: String): CompletableFuture<Note>
    fun tasks(workspaceId: String, noteId: String): CompletableFuture<List<NoteTaskRow>>
    fun watch(
        workspaceId: String,
        onRefresh: () -> Unit,
        onEvent: (NoteEvent) -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable
}

data class NoteEvent(val type: String, val noteId: String?)

data class NoteReaderState(
    val note: Note? = null,
    val tasks: List<NoteTaskRow> = emptyList(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val error: String? = null
)

/** A store owns one visible note. All callbacks and state changes are serialized by its lock. */
class NoteStore(private val source: NoteSource) : Closeable {
    private var lease: Closeable? = null
    private var workspaceId = ""
    private var noteId = ""
    private var generation = 0L
    private var request = 0L
    private val observers = mutableSetOf<(NoteReaderState) -> Unit>()

    var state = NoteReaderState()
        private set

        @Synchronized get

    @Synchronized
    fun observe(observer: (NoteReaderState) -> Unit): Closeable {
        observers.add(observer)
        observer(state)
        return Closeable { synchronized(this) { observers.remove(observer) } }
    }

    @Synchronized
    fun open(workspaceId: String, noteId: String) {
        close()
        this.workspaceId = workspaceId
        this.noteId = noteId
        val current = generation
        publish(NoteReaderState())
        lease = source.watch(
            workspaceId,
            onRefresh = { synchronized(this) { if (current == generation) refresh() } },
            onEvent = { event -> synchronized(this) { if (current == generation) receive(event) } },
            onError = { synchronized(this) { if (current == generation) failed() } }
        )
    }

    @Synchronized
    fun refresh() {
        if (workspaceId.isEmpty()) return
        val current = generation
        val revision = ++request
        publish(state.copy(loading = true, error = null))
        source.note(workspaceId, noteId).thenCombine(source.tasks(workspaceId, noteId)) {
                note,
                tasks
            ->
            NoteReaderState(note = note, tasks = tasks, loading = false)
        }.whenComplete { result, error ->
            synchronized(this) {
                if (current == generation && revision == request) {
                    if (error == null) publish(result) else failed()
                }
            }
        }
    }

    private fun receive(event: NoteEvent) {
        if (event.type == "note:deleted" && event.noteId == noteId) {
            request++
            publish(NoteReaderState(loading = false, deleted = true))
        } else if (event.type.startsWith("task:") || event.noteId == noteId) {
            refresh()
        }
    }

    private fun failed() {
        request++
        publish(
            state.copy(
                loading = false,
                error = "Cannot load this note. Check the connection and retry."
            )
        )
    }

    private fun publish(next: NoteReaderState) {
        state = next
        observers.toList().forEach { it(next) }
    }

    @Synchronized
    override fun close() {
        generation++
        request++
        lease?.close()
        lease = null
        workspaceId = ""
    }
}
