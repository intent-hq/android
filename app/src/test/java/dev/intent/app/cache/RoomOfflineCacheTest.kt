package dev.intent.app.cache

import androidx.room.Room
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomOfflineCacheTest {
    @Test
    fun `disk cache survives reopening with complete note and task data`() {
        val context = RuntimeEnvironment.getApplication()
        val name = "reopen-cache.db"
        context.deleteDatabase(name)
        val value = CachedNote(
            Note("spec", "Plan", content = "# Body", rev = 4),
            listOf(NoteTaskRow(5, "Do it", "done", dependsOn = listOf("other")))
        )
        RoomOfflineCache(
            Room.databaseBuilder(context, CacheDatabase::class.java, name).build(),
            "daemon"
        ).use { cache ->
            cache.replaceWorkspaces(listOf(Workspace("ws", "Workspace"))).join()
            cache.replaceNote("ws", "spec", value).join()
        }
        RoomOfflineCache(
            Room.databaseBuilder(context, CacheDatabase::class.java, name).build(),
            "daemon"
        ).use { cache ->
            assertEquals("Workspace", cache.workspaces().join().single().title)
            assertEquals(value, cache.note("ws", "spec").join())
            cache.replaceNote("ws", "spec", value.copy(tasks = emptyList())).join()
            assertTrue(cache.note("ws", "spec").join()!!.tasks.isEmpty())
            cache.deleteNote("ws", "spec").join()
            assertNull(cache.note("ws", "spec").join())
        }
    }

    @Test
    fun `authoritative workspace replacement prunes removed notes and isolates daemon scopes`() {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        val first = RoomOfflineCache(database, "first")
        val second = RoomOfflineCache(database, "second")
        val value =
            CachedNote(Note("spec", "Plan", content = ""), listOf(NoteTaskRow(1, "Task", "todo")))
        first.replaceWorkspaces(listOf(Workspace("ws", "First"))).join()
        first.replaceNote("ws", "spec", value).join()
        second.replaceWorkspaces(listOf(Workspace("ws", "Second"))).join()
        second.replaceNote("ws", "spec", value).join()
        first.replaceWorkspaces(emptyList()).join()
        assertTrue(first.workspaces().join().isEmpty())
        assertNull(first.note("ws", "spec").join())
        assertEquals("Second", second.workspaces().join().single().title)
        assertEquals(value, second.note("ws", "spec").join())
        first.close()
        second.close()
    }
}
