package dev.intentapp.android.smoke

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import dev.intentapp.android.scripts.ScriptOutputActions
import dev.intentapp.android.scripts.ScriptOutputSheet
import dev.intentapp.android.scripts.ScriptOutputState
import dev.intentapp.android.scripts.WorkspaceScriptDiscovery
import dev.intentapp.android.scripts.WorkspaceScriptsActions
import dev.intentapp.android.scripts.WorkspaceScriptsScreen
import dev.intentapp.android.scripts.WorkspaceScriptsState
import dev.intentapp.android.testing.IntentTestTheme
import dev.intentapp.android.testing.createIntentComposeRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkspaceScriptsTest {
    @get:Rule val compose = createIntentComposeRule()
    private val dark = mutableStateOf(false)
    private val calls = mutableListOf<String>()
    private val actions = WorkspaceScriptsActions(
        launch = { calls += "launch:$it" },
        stop = { calls += "stop:$it" },
        remove = { calls += "remove:$it" },
        showOutput = { calls += "output:$it" },
        reload = { calls += "reload" }
    )

    @Test
    fun rowsRunStopDeleteAndShowOutput() {
        val state = mutableStateOf(loadedState())
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) { WorkspaceScriptsScreen(state.value, actions) }
        }
        compose.onNodeWithText("SCRIPTS").assertIsDisplayed()
        compose.onNodeWithText("Under development").assertDoesNotExist()
        compose.onNodeWithText("Running").assertIsDisplayed()
        compose.onNodeWithText("Failed: port 3000 in use").assertIsDisplayed()
        compose.onNodeWithText("pnpm run lint").assertIsDisplayed()
        screenshot("scripts-light")
        compose.runOnIdle { dark.value = true }
        screenshot("scripts-dark")
        compose.runOnIdle { dark.value = false }

        compose.onNodeWithText("Build").performClick()
        compose.onNodeWithText("Dev server").performClick()
        compose.onNodeWithText("lint").performClick()
        assertEquals(
            listOf("launch:build", "launch:dev", "launch:manifest:package.json:lint"),
            calls
        )

        calls.clear()
        compose.onNodeWithTag("workspace.script.dev").performTouchInput { longClick() }
        compose.onNodeWithText("Show output").performClick()
        compose.onNodeWithTag("workspace.script.dev").performTouchInput { longClick() }
        compose.onNodeWithText("Stop script").performClick()
        compose.onNodeWithTag("workspace.script.build").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Delete script").performClick()
        assertEquals(listOf("output:dev", "stop:dev", "remove:build"), calls)

        calls.clear()
        compose.onNodeWithTag("workspace.script.manifest:package.json:lint")
            .performTouchInput { longClick() }
        compose.onNodeWithText("Delete script").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(pending = setOf("build")) }
        compose.onNodeWithText("Updating").assertIsDisplayed()
        compose.onNodeWithTag("workspace.script.build").assertIsNotEnabled()
    }

    @Test
    fun loadingEmptyErrorAndDisconnectedStates() {
        val state = mutableStateOf(WorkspaceScriptsState(connected = true))
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) { WorkspaceScriptsScreen(state.value, actions) }
        }
        compose.onNodeWithText("Loading scripts…").assertIsDisplayed()
        compose.runOnIdle {
            state.value = WorkspaceScriptsState(loading = false, loaded = true, connected = true)
        }
        compose.onNodeWithText("No scripts").assertIsDisplayed()
        screenshot("scripts-empty-light")
        compose.runOnIdle { dark.value = true }
        screenshot("scripts-empty-dark")
        compose.runOnIdle {
            state.value = state.value.copy(error = "Cannot load scripts.")
        }
        compose.onNodeWithText("No scripts").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("reload"), calls)
        screenshot("scripts-error-dark")
        compose.runOnIdle {
            state.value = loadedState().copy(
                connected = false,
                error = "Scripts are unavailable while disconnected."
            )
        }
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onAllNodesWithText("Disconnected").assertCountEquals(3)
    }

    @Test
    fun outputSheetShowsDetailsOutputAndControls() {
        val state = loadedState()
        val opened = mutableListOf<String>()
        val output = mutableStateOf(
            ScriptOutputState(
                "dev",
                text = "VITE v5.4.2  ready in 312 ms\n\n  ➜  Local:   http://localhost:5173/",
                loading = false
            )
        )
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) {
                WorkspaceScriptsScreen(state, actions)
                ScriptOutputSheet(
                    output.value,
                    state.rows.first { it.id == "dev" },
                    state,
                    ScriptOutputActions(
                        refresh = { calls += "refresh" },
                        stop = { calls += "stop:$it" },
                        openBrowser = { opened += it }
                    )
                )
            }
        }
        compose.onNodeWithText("Script output").assertIsDisplayed()
        compose.onNodeWithText("VITE v5.4.2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Service").assertIsDisplayed()
        screenshot("script-output-light")
        compose.runOnIdle { dark.value = true }
        screenshot("script-output-dark")
        compose.onNodeWithText("Open preview").performClick()
        compose.onNodeWithText("Stop script").performClick()
        compose.onNodeWithText("Refresh output").performClick()
        assertEquals(listOf("http://localhost:5173"), opened)
        assertEquals(listOf("stop:dev", "refresh"), calls)
        compose.runOnIdle {
            output.value = ScriptOutputState("dev", loading = false, error = "Cannot load output.")
        }
        compose.onNodeWithText("Cannot load output.").assertIsDisplayed()
    }

    private fun loadedState() = WorkspaceScriptsState(
        scripts = listOf(
            WorkspaceScriptResource(
                "dev",
                "workspace",
                "Dev server",
                "pnpm run dev",
                "service",
                WorkspaceScriptRuntime("running", detectedUrl = "http://localhost:5173")
            ),
            WorkspaceScriptResource(
                "build",
                "workspace",
                "Build",
                "./gradlew assembleDebug --console=plain",
                "command",
                WorkspaceScriptRuntime("exited", exitCode = 0)
            ),
            WorkspaceScriptResource(
                "storybook",
                "workspace",
                "Storybook",
                "pnpm run storybook",
                "service",
                WorkspaceScriptRuntime("exited", exitCode = 1, error = "Failed: port 3000 in use")
            )
        ),
        discovered = WorkspaceScriptDiscovery.scripts(
            "workspace",
            mapOf("package.json" to """{"scripts":{"dev":"vite","lint":"eslint ."}}"""),
            setOf("package.json", "pnpm-lock.yaml")
        ),
        loading = false,
        loaded = true,
        connected = true
    )

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
