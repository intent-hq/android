package dev.intent.app.cache

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "workspaces", primaryKeys = ["scope", "id"])
data class WorkspaceCacheRow(val scope: String, val id: String, val payload: String)

@Entity(tableName = "notes", primaryKeys = ["scope", "workspaceId", "noteId"])
data class NoteCacheRow(
    val scope: String,
    val workspaceId: String,
    val noteId: String,
    val payload: String
)

@Entity(tableName = "tasks", primaryKeys = ["scope", "workspaceId", "noteId", "position"])
data class TaskCacheRow(
    val scope: String,
    val workspaceId: String,
    val noteId: String,
    val position: Int,
    val payload: String
)

// Room requires separate query methods for the atomic snapshot operations.
@Suppress("TooManyFunctions")
@Dao
abstract class CacheDao {
    @Query("SELECT * FROM workspaces WHERE scope = :scope ORDER BY id")
    abstract fun workspaces(scope: String): List<WorkspaceCacheRow>

    @Query("DELETE FROM workspaces WHERE scope = :scope")
    abstract fun clearWorkspaces(scope: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertWorkspaces(rows: List<WorkspaceCacheRow>)

    @Query("DELETE FROM notes WHERE scope = :scope AND workspaceId NOT IN (:ids)")
    abstract fun pruneNotes(scope: String, ids: List<String>)

    @Query("DELETE FROM tasks WHERE scope = :scope AND workspaceId NOT IN (:ids)")
    abstract fun pruneTasks(scope: String, ids: List<String>)

    @Transaction
    open fun replaceWorkspaces(scope: String, rows: List<WorkspaceCacheRow>) {
        clearWorkspaces(scope)
        insertWorkspaces(rows)
        val ids = rows.map { it.id }
        pruneNotes(scope, ids)
        pruneTasks(scope, ids)
    }

    @Query(
        "SELECT * FROM notes WHERE scope = :scope AND workspaceId = :workspaceId AND noteId = :noteId"
    )
    abstract fun note(scope: String, workspaceId: String, noteId: String): NoteCacheRow?

    @Query(
        "SELECT * FROM tasks WHERE scope = :scope AND workspaceId = :workspaceId AND noteId = :noteId ORDER BY position"
    )
    abstract fun tasks(scope: String, workspaceId: String, noteId: String): List<TaskCacheRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertNote(row: NoteCacheRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertTasks(rows: List<TaskCacheRow>)

    @Query(
        "DELETE FROM notes WHERE scope = :scope AND workspaceId = :workspaceId AND noteId = :noteId"
    )
    abstract fun removeNote(scope: String, workspaceId: String, noteId: String)

    @Query(
        "DELETE FROM tasks WHERE scope = :scope AND workspaceId = :workspaceId AND noteId = :noteId"
    )
    abstract fun removeTasks(scope: String, workspaceId: String, noteId: String)

    @Transaction
    open fun replaceNote(note: NoteCacheRow, tasks: List<TaskCacheRow>) {
        insertNote(note)
        removeTasks(note.scope, note.workspaceId, note.noteId)
        insertTasks(tasks)
    }

    @Transaction
    open fun deleteNote(scope: String, workspaceId: String, noteId: String) {
        removeTasks(scope, workspaceId, noteId)
        removeNote(scope, workspaceId, noteId)
    }
}

@Database(
    entities = [WorkspaceCacheRow::class, NoteCacheRow::class, TaskCacheRow::class],
    version = 1,
    exportSchema = true
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
