package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.browser.BrowserScreen
import dev.intent.app.browser.BrowserState
import dev.intent.app.browser.SavedBrowser
import dev.intent.app.browser.SavedBrowsersState
import dev.intent.app.browser.WorkspaceBrowsersScreen
import dev.intent.app.design.IntentTheme
import dev.intent.app.scripts.WorkspaceScriptsScreen
import dev.intent.app.scripts.WorkspaceScriptsState
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrowserPreviewStateTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun savedBrowserRowsOpenAndRemoveAndShowEmptyState() {
        val browser = SavedBrowser("preview", "Workspace preview", "http://localhost:5173")
        val state = mutableStateOf(SavedBrowsersState(listOf(browser), loading = false))
        val dark = mutableStateOf(false)
        var opened: SavedBrowser? = null
        compose.setContent {
            IntentTheme(darkTheme = dark.value) {
                WorkspaceBrowsersScreen(
                    state.value, {}, {}, { opened = it },
                    { state.value = SavedBrowsersState(loading = false) }, {}
                )
            }
        }
        compose.onNodeWithText("Workspace preview").performClick()
        assertEquals(browser, opened)
        screenshot("browser-resources-light")
        compose.runOnIdle { dark.value = true }
        screenshot("browser-resources-dark")
        compose.onNodeWithText("Remove saved browser").performClick()
        compose.onNodeWithText("No saved browsers").assertIsDisplayed()
    }

    @Test
    fun scriptResourcesOfferStartStopAndPreviewUsingRuntimeState() {
        val scripts = listOf(
            WorkspaceScriptResource(
                "build", "workspace", "Build", "./gradlew build", "command", WorkspaceScriptRuntime("idle")
            ),
            WorkspaceScriptResource(
                "dev", "workspace", "Dev server", "npm run dev", "service",
                WorkspaceScriptRuntime("running", detectedUrl = "http://localhost:5173")
            )
        )
        val dark = mutableStateOf(false)
        var action: Pair<String, Boolean>? = null
        var preview: String? = null
        compose.setContent {
            IntentTheme(darkTheme = dark.value) {
                WorkspaceScriptsScreen(
                    WorkspaceScriptsState(scripts, loading = false, connected = true), {}, {},
                    { id, running -> action = id to running }, { preview = it }
                )
            }
        }
        compose.onNodeWithText("Start").performClick()
        assertEquals("build" to true, action)
        compose.onNodeWithText("Stop").performClick()
        assertEquals("dev" to false, action)
        compose.onNodeWithText("Open preview").performClick()
        assertEquals("http://localhost:5173", preview)
        screenshot("script-resources-light")
        compose.runOnIdle { dark.value = true }
        screenshot("script-resources-dark")
    }

    @Test
    fun previewContentSurvivesActivityRecreation() {
        val state = mutableStateOf(BrowserState(url = "http://localhost:3000"))
        compose.setContent {
            IntentTheme { BrowserScreen(state.value, {}, {}) { Text("Preview page") } }
        }
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.recreateActivity()
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Cannot load preview") }
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Cannot load preview").assertIsDisplayed()
    }

    @Test
    fun failedPageIsRemovedAndRetryIsBesideTheAddress() {
        val state =
            mutableStateOf(
                BrowserState(url = "http://localhost:3000", requestedUrl = "http://localhost:3000")
            )
        var retried = false
        compose.setContent {
            IntentTheme {
                BrowserScreen(state.value, {
                }, {}, onReload = { retried = true }) { Text("Preview page") }
            }
        }
        compose.onNodeWithText("Open a workspace preview").assertDoesNotExist()
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Cannot load preview") }
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Cannot load preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry preview").assertIsDisplayed().performClick()
        assertEquals(true, retried)
        compose.runOnIdle { state.value = BrowserState(loading = true) }
        compose.onNodeWithText("Cannot load preview").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loading preview").assertIsDisplayed()
    }

    @Test
    fun emptyPreviewShowsGuidanceAndAddressEntryOpensTheServer() {
        var opened = ""
        compose.setContent {
            IntentTheme { BrowserScreen(BrowserState(), {}, { opened = it }) {} }
        }
        compose.onNodeWithText("Open a workspace preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Page forward").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Browser URL").performTextInput("localhost:5173/path")
        compose.onNodeWithContentDescription("Browser URL").performImeAction()
        assertEquals("http://localhost:5173/path", opened)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
