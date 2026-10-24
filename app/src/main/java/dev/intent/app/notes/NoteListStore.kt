package dev.intent.app.notes

import dev.intent.core.protocol.models.Note
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NoteListState(
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

class NoteListStore(private val list: () -> CompletableFuture<List<Note>>) : Closeable {
    private val mutableState = MutableStateFlow(NoteListState())
    val state: StateFlow<NoteListState> = mutableState.asStateFlow()
    private var revision = 0L
    private var closed = false

    @Synchronized
    fun refresh() {
        if (closed) return
        val read = ++revision
        mutableState.value = state.value.copy(loading = true, error = null)
        list().whenComplete { notes, error ->
            synchronized(this) {
                if (!closed && read == revision) {
                    mutableState.value = if (error == null) {
                        NoteListState(notes.distinctBy { it.id }, loading = false)
                    } else {
                        state.value.copy(loading = false, error = "Cannot load notes. Try Refresh.")
                    }
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        revision++
    }
}
