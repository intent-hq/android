package dev.intent.app.navigation

import dev.intent.app.notes.NoteListStore
import dev.intent.core.protocol.models.Note
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceDestinationTest {
    @Test
    fun listedNoteReturnsToSectionAndSectionsExitWorkspace() {
        val store = NoteListStore {
            CompletableFuture.completedFuture(listOf(Note("note-123", "Plan")))
        }
        store.refresh()
        val selected = WorkspaceDestination.Note(store.state.value.notes.single().id)
        assertEquals("note-123", selected.id)
        assertEquals(WorkspaceDestination.Notes, selected.parent())
        assertNull(selected.parent()?.parent())
        assertEquals(WorkspaceDestination.Conversations, WorkspaceDestination.Terminals.parent())
        assertNull(WorkspaceDestination.Git.parent())
        assertNull(WorkspaceDestination.Conversations.parent())
    }

    @Test
    fun childrenReturnToTheSectionThatOpenedThem() {
        listOf(
            WorkspaceDestination.Conversations,
            WorkspaceDestination.Notes,
            WorkspaceDestination.Git
        )
            .forEach { origin ->
                assertEquals(origin, WorkspaceDestination.Terminals.parent(origin))
                assertEquals(origin, WorkspaceDestination.Browser.parent(origin))
                assertEquals(origin, WorkspaceDestination.Note("linked").parent(origin))
                assertNull(origin.parent(origin))
            }
    }

    @Test
    fun staleAndDisposedListReadsCannotReplaceCurrentSelectionRows() {
        val reads = mutableListOf<CompletableFuture<List<Note>>>()
        val store = NoteListStore { CompletableFuture<List<Note>>().also { reads.add(it) } }
        store.refresh()
        store.refresh()
        reads[1].complete(listOf(Note("new", "Current")))
        reads[0].complete(listOf(Note("old", "Stale")))
        assertEquals("new", store.state.value.notes.single().id)
        store.refresh()
        store.close()
        reads[2].complete(emptyList())
        assertEquals("new", store.state.value.notes.single().id)
    }

    @Test
    fun failedListCanRetry() {
        var fail = true
        val store = NoteListStore {
            if (fail) {
                CompletableFuture.failedFuture(IllegalStateException("offline"))
            } else {
                CompletableFuture.completedFuture(listOf(Note("note", "Plan")))
            }
        }
        store.refresh()
        assertEquals("Cannot load notes. Try Refresh.", store.state.value.error)
        fail = false
        store.refresh()
        assertNull(store.state.value.error)
        assertEquals("note", store.state.value.notes.single().id)
    }
}
