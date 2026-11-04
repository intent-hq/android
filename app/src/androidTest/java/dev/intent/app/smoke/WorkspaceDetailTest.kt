package dev.intent.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.navigation.WorkspaceDestination
import dev.intent.app.notes.NoteReaderScreen
import dev.intent.app.notes.NoteReaderState
import dev.intent.app.testing.IntentTestTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceDetailScreen
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
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
            IntentTestTheme {
                WorkspaceDetailScreen(
                    Workspace(
                        "work",
                        "Workspace",
                        statusMessage = "Ready for review",
                        displayStatus = "pr_ready",
                        repositoryOwner = "intent-hq",
                        repositoryName = "android"
                    )
                ) { selected = it }
            }
        }
        compose.onNodeWithText("Ready for review").assertIsDisplayed()
        compose.onNodeWithText("Active · PR mergeable").assertIsDisplayed()
        compose.onNodeWithText("intent-hq/android").assertIsDisplayed()
        listOf(
            "Agents" to WorkspaceDestination.Conversations,
            "Context" to WorkspaceDestination.Notes,
            "Changes" to WorkspaceDestination.Git
        ).forEach { (title, destination) ->
            compose.onNodeWithText(title).performClick()
            compose.runOnIdle { assertEquals(destination, selected) }
        }
        screenshot("workspace-detail-hierarchy")
    }

    @Test
    fun detailsExposeFullSummaryAndIndependentStatusSignals() {
        val summary =
            "The repository search and grouping controls are ready. " +
                "Review the archived-workspace filter, the repository owner labels, " +
                "and the waiting indicator before approving this change."
        compose.setContent {
            IntentTestTheme {
                WorkspaceDetailScreen(
                    Workspace(
                        "work",
                        "Workspace",
                        statusMessage = summary,
                        displayStatus = "pr_queued",
                        prNumber = 42,
                        prStatus = "Open",
                        attention = "unread",
                        waiting = true
                    )
                ) {}
            }
        }
        compose.onNodeWithText("Workspace details").performClick()
        listOf(summary, "PR #42 · Open", "Unread updates", "Waiting").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        }
        screenshot("workspace-full-summary")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Agents").assertIsDisplayed()
    }

    @Test
    fun groupingAndRepositorySearchKeepWorkspaceSelection() {
        var selected: String? = null
        compose.setContent {
            IntentTestTheme {
                WorkspaceListScreen(
                    WorkspaceListState(
                        listOf(
                            Workspace(
                                "work",
                                "Improve search",
                                repositoryOwner = "intent-hq",
                                repositoryName = "android",
                                displayStatus = "in_progress",
                                activity = "agent_running",
                                attention = "unread",
                                prNumber = 42
                            ),
                            Workspace("other", "Other work", repositoryName = "ios")
                        ),
                        isLoading = false
                    ),
                    onRefresh = {},
                    onWorkspaceSelected = { selected = it }
                )
            }
        }
        compose.onNodeWithText("Repo").performClick().assertIsSelected()
        screenshot("workspace-repository-groups")
        compose.onNodeWithText("Status").performClick().assertIsSelected()
        screenshot("workspace-status-groups")
        compose.onNodeWithContentDescription("Search workspaces").performClick()
        compose.onNodeWithText("Search workspaces").performTextInput("intent-hq/android")
        compose.onNodeWithText(
            "Improve search",
            substring = true
        ).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("work", selected) }
    }

    @Test
    fun noteHeaderKeepsItsPositionWhileContentScrolls() {
        compose.setContent {
            IntentTestTheme {
                NoteReaderScreen(
                    NoteReaderState(
                        note = Note(
                            "note",
                            "Same title",
                            content = (1..60).joinToString("\n\n") { "Paragraph $it" }
                        ),
                        loading = false
                    ),
                    {}
                )
            }
        }
        val bounds = compose.onNodeWithText("Same title").fetchSemanticsNode().boundsInRoot.topLeft
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Paragraph 60"))
        compose.onNodeWithText("Paragraph 60").assertIsDisplayed()
        assertEquals(
            bounds,
            compose.onNodeWithText("Same title").fetchSemanticsNode().boundsInRoot.topLeft
        )
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory =
            InstrumentationRegistry
                .getArguments()
                .getString("additionalTestOutputDir")
                ?.let { java.io.File(it) }
                ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
