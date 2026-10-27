package dev.intent.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.agents.AgentListScreen
import dev.intent.app.agents.AgentListState
import dev.intent.app.comments.CommentScreen
import dev.intent.app.comments.CommentState
import dev.intent.app.design.IntentTheme
import dev.intent.app.notes.ContextNoteList
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteMetadata
import dev.intent.core.protocol.models.TaskMetadata
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListFiltersTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun agentSearchReopensCollapsedAncestorsAndCurrentKeepsLiveWork() {
        var selected: String? = null
        compose.setContent {
            IntentTheme {
                AgentListScreen(
                    AgentListState(
                        listOf(
                            Agent("root", "Coordinator"),
                            Agent("current", "Fix search", parentAgentId = "root", isActive = true),
                            Agent("old", "Old work", parentAgentId = "root")
                        ),
                        isLoading = false
                    ),
                    {},
                    { selected = it }
                )
            }
        }
        compose.onNodeWithText("▾ Hide 2 delegated agents").performClick()
        compose.onNodeWithText("Fix search").assertDoesNotExist()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search agents"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search agents").performClick()
        compose.onNodeWithText("Search agents").performTextInput("SEARCH")
        compose.onNodeWithText("Coordinator").assertIsDisplayed()
        compose.onNodeWithText("Fix search").performClick()
        compose.runOnIdle { assertEquals("current", selected) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search agents"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search agents").performClick()
        compose.onNodeWithText("Current").performClick()
        compose.onNodeWithText("Old work").assertDoesNotExist()
        compose.onNodeWithText("Fix search").assertIsDisplayed()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Old work").assertIsDisplayed()
    }

    @Test
    fun contextSearchReopensParentsAndCurrentHidesCompletedTasks() {
        var selected: String? = null
        compose.setContent {
            IntentTheme {
                ContextNoteList(
                    listOf(
                        Note("spec", "Plan"),
                        Note(
                            "current",
                            "Fix search",
                            parentId = "spec",
                            metadata = NoteMetadata(TaskMetadata("in_progress"))
                        ),
                        Note(
                            "old",
                            "Old work",
                            parentId = "spec",
                            metadata = NoteMetadata(TaskMetadata("complete"))
                        )
                    ),
                    { selected = it }
                )
            }
        }
        compose.onNodeWithContentDescription("Collapse child notes for Plan").performClick()
        compose.onNodeWithText("Fix search").assertDoesNotExist()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription("Search notes and tasks")
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search notes and tasks").performClick()
        compose.onNodeWithText("Search notes and tasks").performTextInput("SEARCH")
        compose.onNodeWithText("Plan").assertIsDisplayed()
        compose.onNodeWithText("Fix search").performClick()
        compose.runOnIdle { assertEquals("current", selected) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription("Search notes and tasks")
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search notes and tasks").performClick()
        compose.onNodeWithText("Current").performClick()
        compose.onNodeWithText("Old work").assertDoesNotExist()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Old work").assertIsDisplayed()
    }

    @Test
    fun emptyCommentsNamesTheNoteAndExplainsTheSupportedPath() {
        compose.setContent {
            IntentTheme {
                CommentScreen(
                    CommentState(loading = false),
                    null,
                    {},
                    {},
                    { _, _ -> },
                    {},
                    noteTitle = "Release plan"
                )
            }
        }
        compose.onNodeWithText("Release plan").assertIsDisplayed()
        compose.onNodeWithText("No comment threads for this note.").assertIsDisplayed()
        compose.onNodeWithText(
            "To start a thread, open this note in Intent on your computer " +
                "and select text to comment on."
        ).assertIsDisplayed()
        compose.onNodeWithText("Send reply").assertDoesNotExist()
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }
}
