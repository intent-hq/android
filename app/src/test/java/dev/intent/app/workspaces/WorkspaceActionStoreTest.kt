package dev.intent.app.workspaces

import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceActionStoreTest {
    private val row = Workspace("one", "Original")
    private val session = ActionWorkspaceSession()
    private val workspaces = WorkspaceStore(session).also {
        it.refresh()
        session.reads.last().complete(listOf(row))
    }
    private val requests = mutableListOf<Pair<String, JsonObject>>()
    private val replies = mutableListOf<CompletableFuture<JsonElement>>()
    private val actions = WorkspaceActionStore(workspaces) { method, params ->
        requests.add(method to params)
        CompletableFuture<JsonElement>().also(replies::add)
    }

    @Test
    fun renameTrimsAndRequiresMatchingConfirmation() {
        actions.begin(row, WorkspaceAction.Rename)
        actions.submit("  New name  ")
        assertEquals("workspace.update", requests.single().first)
        assertEquals(
            """{"workspaceId":"one","title":"New name"}""",
            requests.single().second.toString()
        )
        reply("""{"workspace":{"id":"other","title":"New name"}}""")
        assertEquals("Original", workspaces.state.value.workspaces.single().title)
        assertNotNull(actions.state.value.error)
        actions.submit(" New name ")
        reply("""{"workspace":{"id":"one","title":"New name"}}""")
        assertEquals("New name", workspaces.state.value.workspaces.single().title)
        assertNull(actions.state.value.action)
    }

    @Test
    fun emptyNameAndUnchangedNameSendNothing() {
        actions.begin(row, WorkspaceAction.Rename)
        actions.submit("   ")
        assertEquals("Enter a workspace name.", actions.state.value.error)
        actions.submit(" Original ")
        assertNull(actions.state.value.action)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun archiveChecksStatusAndKeepsArchivedRowForShowArchived() {
        actions.begin(row, WorkspaceAction.Archive)
        actions.submit()
        assertEquals("workspace.archive", requests.single().first)
        assertEquals("""{"workspaceId":"one"}""", requests.single().second.toString())
        reply("""{"workspace":{"id":"one","status":"Active"}}""")
        assertEquals("Active", workspaces.state.value.workspaces.single().status)
        assertNotNull(actions.state.value.error)
        actions.submit()
        reply("""{"workspace":{"id":"one","status":"Archived"}}""")
        assertEquals("Archived", workspaces.state.value.workspaces.single().status)
        assertEquals("one", actions.state.value.removedId)
    }

    @Test
    fun deleteFailureRetainsRowAndRetryRemovesOnlyAfterSuccess() {
        actions.begin(row, WorkspaceAction.Delete)
        actions.submit()
        assertEquals("workspace.delete", requests.single().first)
        assertEquals("""{"workspaceId":"one"}""", requests.single().second.toString())
        replies.last().completeExceptionally(IllegalStateException("Offline"))
        assertEquals(listOf(row), workspaces.state.value.workspaces)
        assertEquals("Offline", actions.state.value.error)
        actions.submit()
        reply("""{"success":false}""")
        assertEquals(listOf(row), workspaces.state.value.workspaces)
        actions.submit()
        reply("""{"success":true}""")
        assertTrue(workspaces.state.value.workspaces.isEmpty())
        assertEquals("one", actions.state.value.removedId)
    }

    @Test
    fun cancelSendsNothingAndPendingMutationCannotBeSubmittedTwice() {
        actions.begin(row, WorkspaceAction.Delete)
        actions.cancel()
        actions.submit()
        assertTrue(requests.isEmpty())
        actions.begin(row, WorkspaceAction.Delete)
        actions.submit()
        actions.submit()
        actions.cancel()
        assertEquals(1, requests.size)
        assertTrue(actions.state.value.busy)
    }

    @Test
    fun closedActionsIgnoreLateConfirmation() {
        actions.begin(row, WorkspaceAction.Delete)
        actions.submit()
        actions.close()
        reply("""{"success":true}""")
        assertEquals(listOf(row), workspaces.state.value.workspaces)
    }

    @Test
    fun confirmedChangeFencesOlderListRead() {
        workspaces.refresh()
        actions.begin(row, WorkspaceAction.Delete)
        actions.submit()
        reply("""{"success":true}""")
        session.reads.last().complete(listOf(row))
        assertTrue(workspaces.state.value.workspaces.isEmpty())
        assertFalse(workspaces.state.value.isLoading)
    }

    @Test
    fun recentAgentUsesParsedTimeIncludesDelegatesAndExcludesRetired() {
        val agents = listOf(
            Agent("whole", "Whole", lastActivity = "2026-10-26T12:00:00Z"),
            Agent(
                "fraction",
                "Fraction",
                parentAgentId = "whole",
                lastActivity = "2026-10-26T12:00:00.500Z"
            ),
            Agent("retired", "Retired", lastActivity = "2026-10-27T12:00:00Z", retiredAt = "now"),
            Agent("bad", "Bad", lastActivity = "invalid")
        )
        assertEquals("fraction", mostRecentWorkspaceAgent(agents)?.id)
        assertEquals("a", mostRecentWorkspaceAgent(listOf(Agent("b", "B"), Agent("a", "A")))?.id)
        assertNull(mostRecentWorkspaceAgent(emptyList()))
    }

    private fun reply(json: String) = replies.last().complete(Json.parseToJsonElement(json))
}

private class ActionWorkspaceSession : WorkspaceSession {
    val reads = mutableListOf<CompletableFuture<List<Workspace>>>()
    override fun list(): CompletableFuture<List<Workspace>> =
        CompletableFuture<List<Workspace>>().also(reads::add)

    override fun subscribe(
        onPush: (SubscriptionPush<Workspace>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable = Closeable {}
}
