package dev.intent.app.smoke

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.agents.AgentListRoute
import dev.intent.app.agents.AgentSession
import dev.intent.app.agents.AgentStore
import dev.intent.app.comments.CommentScreen
import dev.intent.app.comments.CommentState
import dev.intent.app.design.IntentTheme
import dev.intent.app.git.GitScreen
import dev.intent.app.git.GitState
import dev.intent.app.notes.NoteListScreen
import dev.intent.app.notes.NoteListStore
import dev.intent.app.terminals.TerminalListScreen
import dev.intent.app.terminals.TerminalState
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceListRoute
import dev.intent.app.workspaces.WorkspaceSession
import dev.intent.app.workspaces.WorkspaceStore
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.SubscriptionPush
import dev.intent.core.protocol.models.Workspace
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListRefreshTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun workspacesPullFromEmptyRetainsRowsOnErrorAndRetries() {
        val source = RefreshWorkspaceSession()
        val store = WorkspaceStore(source)
        compose.setContent { IntentTheme { WorkspaceListRoute(store, {}) } }
        compose.runOnIdle { source.reads.last().complete(emptyList()) }
        pull()
        compose.runOnIdle {
            assertEquals(2, source.reads.size)
            assertTrue(store.state.value.isLoading)
        }
        assertLoading()
        compose.runOnIdle {
            source.reads.last().complete(listOf(Workspace("w", "Workspace row")))
        }
        assertNotLoading()
        compose.onNodeWithText("Workspace row").assertIsDisplayed()
        pull()
        compose.runOnIdle {
            assertTrue(store.state.value.isLoading)
            source.reads.last().completeExceptionally(IllegalStateException("Offline"))
            assertFalse(store.state.value.isLoading)
        }
        assertNotLoading()
        compose.onNodeWithText("Workspace row").assertIsDisplayed()
        compose.onNodeWithText("Offline").assertIsDisplayed()
        refreshAction()
        compose.runOnIdle {
            assertEquals(4, source.reads.size)
            source.reads.last().complete(emptyList())
        }
        compose.onNodeWithText("No workspaces yet").assertIsDisplayed()
    }

    @Test
    fun agentsPullFromErrorRetainsRowsAndStopsLoading() {
        val source = RefreshAgentSession()
        val store = AgentStore(source, "w")
        compose.setContent { IntentTheme { AgentListRoute(store, {}) } }
        compose.runOnIdle {
            source.reads.last().completeExceptionally(IllegalStateException("Offline"))
        }
        pull()
        compose.runOnIdle {
            assertEquals(2, source.reads.size)
            assertTrue(store.state.value.isLoading)
        }
        assertLoading()
        compose.runOnIdle {
            source.reads.last().complete(listOf(Agent("a", "Agent row")))
        }
        assertNotLoading()
        compose.onNodeWithText("Agent row").assertIsDisplayed()
        pull()
        compose.runOnIdle {
            source.reads.last().completeExceptionally(IllegalStateException("Offline"))
            assertFalse(store.state.value.isLoading)
        }
        assertNotLoading()
        compose.onNodeWithText("Agent row").assertIsDisplayed()
        refreshAction()
        compose.runOnIdle {
            source.reads.last().complete(emptyList())
            assertFalse(store.state.value.isLoading)
        }
        compose.onNodeWithText("No agents yet").assertIsDisplayed()
    }

    @Test
    fun contextPullFromEmptyRetainsNotesOnErrorAndRetries() {
        val reads = mutableListOf<CompletableFuture<List<Note>>>()
        val store = NoteListStore { CompletableFuture<List<Note>>().also(reads::add) }
        store.refresh()
        reads.last().complete(emptyList())
        compose.setContent {
            val state by store.state.collectAsState()
            IntentTheme { NoteListScreen(state, store::refresh, {}) }
        }
        pull()
        compose.runOnIdle {
            assertEquals(2, reads.size)
            assertTrue(store.state.value.loading)
        }
        assertLoading()
        compose.runOnIdle {
            reads.last().complete(listOf(Note("n", "Context row")))
        }
        assertNotLoading()
        compose.onNodeWithText("Context row").assertIsDisplayed()
        pull()
        compose.runOnIdle {
            reads.last().completeExceptionally(IllegalStateException("Offline"))
            assertFalse(store.state.value.loading)
        }
        assertNotLoading()
        compose.onNodeWithText("Context row").assertIsDisplayed()
        pull()
        compose.runOnIdle {
            assertEquals(4, reads.size)
            reads.last().complete(emptyList())
            assertFalse(store.state.value.loading)
        }
        compose.onNodeWithText("No notes").assertIsDisplayed()
    }

    @Test
    fun changesCommentsAndTerminalsRefreshFromEmptyAndErrorStates() {
        val screen = mutableStateOf(0)
        var calls = 0
        compose.setContent {
            IntentTheme {
                when (screen.value) {
                    0 -> GitScreen(GitState(loading = false, error = "Offline"), {
                    }, { calls++ }, {})
                    1 -> CommentScreen(CommentState(loading = false), null, {
                    }, { calls++ }, { _, _ -> }, {})
                    else -> TerminalListScreen(TerminalState(loading = false), { calls++ }, {})
                }
            }
        }
        repeat(3) { index ->
            compose.runOnIdle { screen.value = index }
            pull()
            compose.runOnIdle { assertEquals(index * 2 + 1, calls) }
            refreshAction()
            compose.runOnIdle { assertEquals(index * 2 + 2, calls) }
        }
    }

    private fun assertLoading() {
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        )
            .assertIsDisplayed()
    }

    private fun assertNotLoading() {
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        ).assertDoesNotExist()
    }

    private fun pull() {
        compose.onNodeWithTag("refreshable-list").performTouchInput { swipeDown() }
        compose.waitForIdle()
    }

    private fun refreshAction() {
        val actions = compose.onNodeWithTag("refreshable-list")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.single { it.label == "Refresh" }.action()) }
    }
}

private class RefreshWorkspaceSession : WorkspaceSession {
    val reads = mutableListOf<CompletableFuture<List<Workspace>>>()
    override fun list() = CompletableFuture<List<Workspace>>().also(reads::add)
    override fun subscribe(
        onPush: (SubscriptionPush<Workspace>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        onSubscribed()
        return Closeable {}
    }
}

private class RefreshAgentSession : AgentSession {
    val reads = mutableListOf<CompletableFuture<List<Agent>>>()
    override fun list() = CompletableFuture<List<Agent>>().also(reads::add)
    override fun subscribe(
        onPush: (SubscriptionPush<Agent>) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        onSubscribed()
        return Closeable {}
    }
}
