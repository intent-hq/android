package dev.intent.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun agentDelegationExpandsAndSelectsChildAgent() {
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
        compose.onNodeWithText("+2 delegated · 1 running").assertIsDisplayed()
        compose.onNodeWithText("Fix search").assertDoesNotExist()
        compose.onNodeWithText("+2 delegated · 1 running").performClick()
        compose.onNodeWithText("Fix search").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("current", selected) }
        compose.onNodeWithText("Old work").assertIsDisplayed()
    }

    @Test
    fun contextNotesCollapseAndExpandChildren() {
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
        compose.onNodeWithText("Fix search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse child notes for Plan").performClick()
        compose.onNodeWithText("Fix search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand child notes for Plan").performClick()
        compose.onNodeWithText("Fix search").performClick()
        compose.runOnIdle { assertEquals("current", selected) }
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
