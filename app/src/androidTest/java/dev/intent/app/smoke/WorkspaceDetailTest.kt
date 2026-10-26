package dev.intent.app.smoke

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.design.IntentTheme
import dev.intent.app.navigation.WorkspaceDestination
import dev.intent.app.notes.NoteReaderScreen
import dev.intent.app.notes.NoteReaderState
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceDetailScreen
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.Workspace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceDetailTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun summaryStateAndToolsAreReachable() {
        var selected: WorkspaceDestination? = null
        compose.setContent {
            IntentTheme {
                WorkspaceDetailScreen(
                    Workspace(
                        "work",
                        "Workspace",
                        statusMessage = "Ready for review",
                        displayStatus = "pr_ready"
                    )
                ) { selected = it }
            }
        }
        compose.onNodeWithText("Ready for review").assertIsDisplayed()
        compose.onNodeWithText("Active · PR ready").assertIsDisplayed()
        listOf(
            "Agents" to WorkspaceDestination.Conversations,
            "Context" to WorkspaceDestination.Notes,
            "Changes" to WorkspaceDestination.Git,
            "Browser preview" to WorkspaceDestination.Browser,
            "Terminals" to WorkspaceDestination.Terminals
        ).forEach { (title, destination) ->
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(destination, selected) }
        }
    }

    @Test
    fun noteHeaderKeepsItsPositionWhileContentScrolls() {
        val detail = mutableStateOf(true)
        compose.setContent {
            IntentTheme {
                if (detail.value) {
                    WorkspaceDetailScreen(Workspace("work", "Same title")) {}
                } else {
                    NoteReaderScreen(
                        NoteReaderState(
                            note = Note(
                                "note",
                                "Same title",
                                content = (1..60).joinToString("\n\n") {
                                    "Paragraph $it"
                                }
                            ),
                            loading = false
                        ),
                        {}
                    )
                }
            }
        }
        val bounds = compose.onNodeWithText("Same title").fetchSemanticsNode().boundsInRoot.topLeft
        compose.runOnIdle { detail.value = false }
        assertEquals(
            bounds,
            compose.onNodeWithText("Same title").fetchSemanticsNode().boundsInRoot.topLeft
        )
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Paragraph 60"))
        compose.onNodeWithText("Paragraph 60").assertIsDisplayed()
        assertEquals(
            bounds,
            compose.onNodeWithText("Same title").fetchSemanticsNode().boundsInRoot.topLeft
        )
    }
}
