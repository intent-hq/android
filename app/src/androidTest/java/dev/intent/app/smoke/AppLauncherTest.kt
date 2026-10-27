package dev.intent.app.smoke

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.navigation.LauncherActions
import dev.intent.app.navigation.LocalLauncherActions
import dev.intent.app.settings.Appearance
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
import dev.intent.core.protocol.models.Workspace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLauncherTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun launcherTilesNavigateCycleAppearanceAndKeepTheUnderlyingPage() {
        var selected = ""
        compose.setContent {
            var appearance by remember { mutableStateOf(Appearance.System) }
            IntentTheme(darkTheme = appearance == Appearance.Dark) {
                CompositionLocalProvider(
                    LocalLauncherActions provides LauncherActions(
                        2,
                        appearance,
                        { appearance = Appearance.entries[(appearance.ordinal + 1) % 3] },
                        { selected = "connections" },
                        { selected = "settings" },
                        { selected = "create" }
                    )
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkspaceListScreen(
                            WorkspaceListState(listOf(Workspace("a", "Alpha")), false),
                            {},
                            {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithContentDescription("Workspaces").assertDoesNotExist()
        screenshot("app-launcher")
        tile("New workspace")
        compose.runOnIdle { assertEquals("create", selected) }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        tile("Connections: 2")
        compose.runOnIdle { assertEquals("connections", selected) }
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        tile("Appearance: System")
        compose.onNodeWithText("Appearance: Light").assertIsDisplayed()
        tile("Appearance: Light")
        compose.onNodeWithText("Appearance: Dark").assertIsDisplayed()
        screenshot("app-launcher-dark")
        tile("Appearance: Dark")
        compose.onNodeWithText("Appearance: System").assertIsDisplayed()
        tile("Settings")
        compose.runOnIdle { assertEquals("settings", selected) }
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithContentDescription("Close app launcher").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithContentDescription("Close app launcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithContentDescription("Dismiss app launcher").performTouchInput {
            click(Offset(center.x, height / 4f))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Close app launcher"))
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Search workspaces").performClick()
        compose.onNodeWithContentDescription("Open app launcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithContentDescription("Open app launcher").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
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

    private fun tile(text: String) {
        compose.onNodeWithTag("launcher.tiles").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
}
