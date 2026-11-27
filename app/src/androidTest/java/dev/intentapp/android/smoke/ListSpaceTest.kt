package dev.intentapp.android.smoke

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.core.protocol.models.Workspace
import dev.intentapp.android.navigation.LauncherActions
import dev.intentapp.android.navigation.LocalLauncherActions
import dev.intentapp.android.settings.Appearance
import dev.intentapp.android.testing.IntentTestTheme
import dev.intentapp.android.testing.createIntentComposeRule
import dev.intentapp.android.workspaces.WorkspaceListScreen
import dev.intentapp.android.workspaces.WorkspaceListState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListSpaceTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun workspaceLastRowClearsLauncherAndSearchPreservesGrouping() {
        compose.setContent {
            IntentTestTheme {
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
        screenshot("workspace-grouping-top")
        compose.onNode(hasScrollAction()).performScrollToIndex(4)
        compose.onNode(hasScrollAction()).performTouchInput {
            swipe(
                androidx.compose.ui.geometry.Offset(centerX, height * 0.6f),
                androidx.compose.ui.geometry.Offset(centerX, height * 0.6f - 144f),
                durationMillis = 1_000
            )
        }
        screenshot("workspace-grouping-fade")
        compose.onNodeWithText("Repo").performClick()
        compose.onNodeWithContentDescription("Search workspaces").performClick()
        compose.onNodeWithText("Search workspaces").performTextInput("Workspace 20")
        compose.onNode(hasText("Workspace 20") and !hasSetTextAction()).assertIsDisplayed()
        screenshot("workspace-grouping-search")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithText("Repo").assertIsSelected()
        compose.onNodeWithText("Recent").performClick()
        scrollToEnd()
        assertAboveButton("Workspace 20", "Open app launcher")
        screenshot("workspace-button-clearance")
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithText("Connections: 1").assertIsDisplayed()
        screenshot("workspace-intent-launcher")
        compose.onNodeWithTag("launcher.tiles").performScrollToIndex(2)
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close app launcher").performClick()
        compose.onNodeWithText("Workspace 20").assertIsDisplayed()
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
