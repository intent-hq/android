package dev.intent.app.cache

import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.Workspace
import java.util.concurrent.CompletableFuture

data class CachedNote(val note: Note, val tasks: List<NoteTaskRow>)

/** One cache belongs to one paired daemon and principal. Operations must preserve submission order. */
interface OfflineCache {
    fun workspaces(): CompletableFuture<List<Workspace>>
    fun replaceWorkspaces(rows: List<Workspace>): CompletableFuture<Unit>
    fun note(workspaceId: String, noteId: String): CompletableFuture<CachedNote?>
    fun replaceNote(workspaceId: String, noteId: String, value: CachedNote): CompletableFuture<Unit>
    fun deleteNote(workspaceId: String, noteId: String): CompletableFuture<Unit>
}
