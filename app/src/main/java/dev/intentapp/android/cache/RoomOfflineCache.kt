package dev.intentapp.android.cache

import android.content.Context
import androidx.room.Room
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The connection owner closes this cache after its stores. No database work runs on a session callback. */
class RoomOfflineCache internal constructor(
    internal val database: CacheDatabase,
    private val scope: String,
    private val executor: ExecutorService,
    private val ownsDatabase: Boolean,
    private val ownsExecutor: Boolean
) : OfflineCache,
    Closeable {
    constructor(database: CacheDatabase, scope: String) : this(
        database = database,
        scope = scope,
        executor = Executors.newSingleThreadExecutor(),
        ownsDatabase = true,
        ownsExecutor = true
    )

    private val lock = Any()
    private var closed = false
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

    private fun <T> submit(action: () -> T): CompletableFuture<T> = synchronized(lock) {
        if (closed) {
            CompletableFuture<T>().apply {
                completeExceptionally(IllegalStateException("Offline cache is closed"))
            }
        } else {
            CompletableFuture.supplyAsync(action, executor)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            if (ownsDatabase) executor.execute { database.close() }
            if (ownsExecutor) executor.shutdown()
        }
    }

    companion object {
        private const val DATABASE_NAME = "offline-cache.db"
        private var sharedApp: Context? = null
        private var sharedDatabase: CacheDatabase? = null
        private val sharedExecutor: ExecutorService = Executors.newSingleThreadExecutor()

        /** Use a stable pairing ID, including the principal identity; never use a bearer token. */
        fun open(context: Context, scope: String): RoomOfflineCache = RoomOfflineCache(
            database = sharedDatabase(context),
            scope = scope,
            executor = sharedExecutor,
            ownsDatabase = false,
            ownsExecutor = false
        )

        @Synchronized
        private fun sharedDatabase(context: Context): CacheDatabase {
            val app = context.applicationContext
            val existing = sharedDatabase
            if (existing != null && sharedApp === app) return existing
            val created = Room.databaseBuilder(app, CacheDatabase::class.java, DATABASE_NAME)
                .build()
            sharedApp = app
            sharedDatabase = created
            existing?.let { old -> sharedExecutor.execute { old.close() } }
            return created
        }
    }
}
