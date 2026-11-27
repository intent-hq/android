package dev.intentapp.android.smoke

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.core.protocol.models.Workspace
import dev.intentapp.android.navigation.LauncherActions
import dev.intentapp.android.navigation.LocalLauncherActions
import dev.intentapp.android.settings.Appearance
import dev.intentapp.android.testing.IntentTestTheme
import dev.intentapp.android.testing.createIntentComposeRule
import dev.intentapp.android.workspaces.WorkspaceListScreen
import dev.intentapp.android.workspaces.WorkspaceListState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IntentLauncherTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun openingAndClosingReverseContinuously() {
        content()
        compose.mainClock.autoAdvance = false
        screenshot("intent-00-closed")
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        repeat(6) {
            compose.mainClock.advanceTimeBy(48)
            screenshot("intent-0${it + 1}-opening")
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription(
            "Close app launcher"
        ).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(80)
        screenshot("intent-07-reversing")
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Close app launcher").assertIsDisplayed()
        screenshot("intent-08-open")
        compose.onNodeWithContentDescription("Close app launcher").performClick()
        repeat(6) {
            compose.mainClock.advanceTimeBy(48)
            screenshot("intent-${it + 10}-closing")
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Open app launcher").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close app launcher").assertDoesNotExist()
        screenshot("intent-16-closed")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun appearanceStaysOpenAndSettingsRunsOnceAfterScrimDismissal() {
        var settings = 0
        content { settings++ }
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithText("Appearance: Light").performClick()
        compose.onNodeWithText("Appearance: Dark").assertIsDisplayed()
        screenshot("intent-dark-open")
        compose.onNodeWithContentDescription("Dismiss app launcher").performClick()
        compose.onNodeWithContentDescription("Open app launcher").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open app launcher").performClick()
        compose.onNodeWithTag("launcher.tiles").performScrollToIndex(2)
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithContentDescription("Open app launcher").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, settings) }
    }

    private fun content(settings: () -> Unit = {}) {
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            IntentTestTheme(dark) {
                CompositionLocalProvider(
                    LocalLauncherActions provides LauncherActions(
                        1,
                        if (dark) Appearance.Dark else Appearance.Light,
                        { dark = !dark },
                        {},
                        settings
                    )
                ) {
                    WorkspaceListScreen(
                        WorkspaceListState(
                            listOf(
                                Workspace(
                                    "workspace",
                                    "Intent menu preview",
                                    statusMessage = "Description stays in workspace details."
                                )
                            ),
                            false
                        ),
                        {},
                        {}
                    )
                }
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(100, 5_000)
        val root = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(instrumentation.targetContext.cacheDir, "screenshots").path
        val directory = File(root).apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
