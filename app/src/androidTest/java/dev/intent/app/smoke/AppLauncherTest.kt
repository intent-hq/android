package dev.intent.app.smoke

import android.view.KeyEvent
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
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
        var activityRoot: View? = null
        compose.setContent {
            val root = LocalView.current.rootView
            SideEffect { activityRoot = root }
            var appearance by remember { mutableStateOf(Appearance.System) }
            IntentTheme(darkTheme = appearance == Appearance.Dark) {
                CompositionLocalProvider(
                    LocalLauncherActions provides LauncherActions(
                        2,
                        appearance,
                        { appearance = Appearance.entries[(appearance.ordinal + 1) % 3] },
                        { selected = "connections" },
                        { selected = "settings" },
                        { selected = "create" },
                        onAssistant = { selected = "assistant" }
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
        tile("Assistant")
        compose.runOnIdle { assertEquals("assistant", selected) }
        awaitLauncherClosed()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        tile("New workspace")
        compose.runOnIdle { assertEquals("create", selected) }
        awaitLauncherClosed()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        tile("Connections: 2")
        compose.runOnIdle { assertEquals("connections", selected) }
        awaitLauncherClosed()
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
        awaitLauncherClosed()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithContentDescription("Close app launcher").assertIsDisplayed()
        // Wait for the dialog window to receive focus before dispatching system Back.
        var initiallyFocused = false
        compose.runOnIdle {
            initiallyFocused = WindowInspector.getGlobalWindowViews().any {
                it !== activityRoot && it.hasWindowFocus()
            }
        }
        android.util.Log.i("LauncherFocus", "Dialog focused before wait: $initiallyFocused")
        try {
            compose.waitUntil(5_000) {
                var focused = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    focused = WindowInspector.getGlobalWindowViews().any {
                        it !== activityRoot && it.hasWindowFocus()
                    }
                }
                focused
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            screenshot("launcher-focus-failure")
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("dumpsys window windows")
            val windows = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
            android.util.Log.e("LauncherFocus", windows)
            throw error
        }
        android.util.Log.i("LauncherFocus", "Dialog focused; sending real Back key")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        awaitLauncherClosed()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithContentDescription("Dismiss app launcher").performTouchInput {
            click(Offset(center.x, height / 4f))
        }
        awaitLauncherClosed()
        compose.onNodeWithContentDescription("Search workspaces").performClick()
        compose.onNodeWithContentDescription("Open app launcher").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithContentDescription("Open app launcher").assertIsDisplayed()
    }

    private fun awaitLauncherClosed() {
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Close app launcher"))
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Close app launcher").assertDoesNotExist()
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
