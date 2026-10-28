package dev.intent.app.smoke

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.agents.AgentListScreen
import dev.intent.app.agents.AgentListState
import dev.intent.app.design.IntentTheme
import dev.intent.app.navigation.LauncherActions
import dev.intent.app.navigation.LocalLauncherActions
import dev.intent.app.navigation.LocalWorkspaceSectionHeader
import dev.intent.app.navigation.WorkspaceDestination
import dev.intent.app.notes.NoteListScreen
import dev.intent.app.notes.NoteListState
import dev.intent.app.settings.Appearance
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceDetailScreen
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.Workspace
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListSpaceTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun workspaceLastRowClearsBothFloatingButtons() {
        compose.setContent {
            IntentTheme {
                CompositionLocalProvider(
                    LocalLauncherActions provides LauncherActions(1, Appearance.Dark, {}, {}, {})
                ) {
                    WorkspaceListScreen(
                        WorkspaceListState(
                            (1..20).map {
                                Workspace("$it", "Workspace $it")
                            },
                            isLoading = false
                        ),
                        {},
                        {}
                    )
                }
            }
        }
        scrollToEnd()
        assertAboveButton("Workspace 20", "Search workspaces")
        assertAboveButton("Workspace 20", "Open app launcher")
        screenshot("workspace-button-clearance")
    }

    @Test
    fun agentFilterIsVisibleAndLastRowClearsSearch() {
        compose.setContent {
            IntentTheme {
                AgentListScreen(
                    AgentListState(
                        (1..20).map {
                            Agent(it.toString().padStart(2, '0'), "Agent $it")
                        },
                        isLoading = false
                    ),
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithText("Current").assertIsDisplayed()
        compose.onNodeWithText("All").assertIsSelected()
        scrollToEnd()
        assertAboveButton("Agent 20", "Search agents")
        screenshot("agent-button-clearance")
    }

    @Test
    fun noteFilterPersistsAfterSearchAndResultsStayCompact() {
        compose.setContent {
            IntentTheme {
                CompositionLocalProvider(
                    LocalWorkspaceSectionHeader provides { actions ->
                        WorkspaceDetailScreen(
                            Workspace("workspace", "Review workspace"),
                            WorkspaceDestination.Notes,
                            actions
                        ) {}
                    }
                ) {
                    NoteListScreen(
                        NoteListState((1..20).map { Note("$it", "Note $it") }, loading = false),
                        {},
                        {},
                        files = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Current").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Search notes and tasks").performClick()
        compose.onNodeWithText("Search notes and tasks").performClick().performTextInput("Note")
        compose.onNodeWithText("Note 1").assertIsDisplayed()
        compose.onNodeWithText("Note 2").assertIsDisplayed()
        compose.onNodeWithText("Note 3").assertIsDisplayed()
        screenshot("note-search-room")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithText("Current").assertIsSelected()
        scrollToEnd()
        assertAboveButton("Note 20", "Search notes and tasks")
    }

    private fun scrollToEnd() {
        compose.onNode(hasScrollAction()).performScrollToIndex(19)
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
    }

    private fun assertAboveButton(text: String, button: String) {
        compose.onNodeWithText(text).assertIsDisplayed()
        val row = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val control = compose.onNodeWithContentDescription(button).fetchSemanticsNode().boundsInRoot
        assertTrue("$text must clear $button", row.bottom < control.top)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
