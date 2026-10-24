package dev.intent.app.cache

import android.content.Context
import androidx.room.Room
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** The connection owner closes this cache after its stores. No database work runs on a session callback. */
class RoomOfflineCache(private val database: CacheDatabase, private val scope: String) :
    OfflineCache,
    Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dao = database.cacheDao()

    init {
        require(scope.isNotBlank()) { "Cache scope must identify the paired daemon and principal" }
    }

    override fun workspaces(): CompletableFuture<List<Workspace>> = submit {
        dao.workspaces(scope).map { WireJson.decodeFromString(Workspace.serializer(), it.payload) }
    }

    override fun replaceWorkspaces(rows: List<Workspace>): CompletableFuture<Unit> = submit {
        dao.replaceWorkspaces(
            scope,
            rows.map {
                WorkspaceCacheRow(scope, it.id, WireJson.encodeToString(Workspace.serializer(), it))
            }
        )
    }

    override fun note(workspaceId: String, noteId: String): CompletableFuture<CachedNote?> =
        submit {
            var result: CachedNote? = null
            database.runInTransaction {
                dao.note(scope, workspaceId, noteId)?.let { row ->
                    result = CachedNote(
                        WireJson.decodeFromString(Note.serializer(), row.payload),
                        dao.tasks(scope, workspaceId, noteId).map {
                            WireJson.decodeFromString(NoteTaskRow.serializer(), it.payload)
                        }
                    )
                }
            }
            result
        }

    override fun replaceNote(
        workspaceId: String,
        noteId: String,
        value: CachedNote
    ): CompletableFuture<Unit> = submit {
        dao.replaceNote(
            NoteCacheRow(
                scope,
                workspaceId,
                noteId,
                WireJson.encodeToString(Note.serializer(), value.note)
            ),
            value.tasks.mapIndexed { index, row ->
                TaskCacheRow(
                    scope,
                    workspaceId,
                    noteId,
                    index,
                    WireJson.encodeToString(NoteTaskRow.serializer(), row)
                )
            }
        )
    }

    override fun deleteNote(workspaceId: String, noteId: String): CompletableFuture<Unit> = submit {
        dao.deleteNote(scope, workspaceId, noteId)
    }

    private fun <T> submit(action: () -> T): CompletableFuture<T> =
        CompletableFuture.supplyAsync(action, executor)

    override fun close() {
        executor.execute { database.close() }
        executor.shutdown()
    }

    companion object {
        /** Use a stable pairing ID, including the principal identity; never use a bearer token. */
        fun open(context: Context, scope: String): RoomOfflineCache = RoomOfflineCache(
            Room.databaseBuilder(
                context.applicationContext,
                CacheDatabase::class.java,
                "offline-cache.db"
            ).build(),
            scope
        )
    }
}
