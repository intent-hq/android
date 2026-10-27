package dev.intent.app.smoke

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
import dev.intent.core.protocol.models.Workspace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageSearchTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun workspaceSearchClearsAndBackRestoresTheList() {
        compose.setContent {
            IntentTheme {
                WorkspaceListScreen(
                    WorkspaceListState(
                        listOf(Workspace("a", "Alpha"), Workspace("b", "Beta")),
                        isLoading = false
                    ),
                    {},
                    {}
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search workspaces"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search workspaces").performClick()
        compose.onNodeWithText("Search workspaces").performTextInput("Alpha")
        compose.onNodeWithText("Beta").assertDoesNotExist()
        screenshot("workspace-search")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Beta").assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search workspaces"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search workspaces").performClick()
        compose.onNodeWithText("Search workspaces").performTextInput("missing")
        compose.onNodeWithText("No matching workspaces").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun emptyListHasNoSearchButton() {
        compose.setContent {
            IntentTheme { WorkspaceListScreen(WorkspaceListState(isLoading = false), {}, {}) }
        }
        compose.onNodeWithContentDescription("Search workspaces").assertDoesNotExist()
    }

    @Test
    fun twoLineHeaderCentersIconsAndDoesNotInheritActions() {
        val title = "A title that wraps onto two lines"
        compose.setContent {
            IntentTheme {
                CompositionLocalProvider(
                    LocalChromeActions provides listOf(ChromeAction("Settings") {})
                ) {
                    Column(Modifier.width(280.dp)) {
                        CompactAppBar(title, onBack = {
                        }, actions = listOf(ChromeAction("Refresh") {}))
                    }
                }
            }
        }
        val titleBounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        for (label in listOf("Back", "More actions")) {
            val bounds = compose.onNodeWithContentDescription(
                label
            ).fetchSemanticsNode().boundsInRoot
            assertEquals(titleBounds.center.y, bounds.center.y, 1f)
        }
        screenshot("two-line-header")
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
