package dev.intentapp.android.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.models.TerminalInfo
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import dev.intentapp.android.agents.AgentListResources
import dev.intentapp.android.agents.AgentListScreen
import dev.intentapp.android.agents.AgentListState
import dev.intentapp.android.browser.SavedBrowser
import dev.intentapp.android.browser.SavedBrowsersState
import dev.intentapp.android.browser.WorkspaceBrowsersHandle
import dev.intentapp.android.browser.workspaceBrowsersSection
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.horizontalBleed
import dev.intentapp.android.scripts.WorkspaceScriptDiscovery
import dev.intentapp.android.scripts.WorkspaceScriptsActions
import dev.intentapp.android.scripts.WorkspaceScriptsState
import dev.intentapp.android.scripts.workspaceScriptsSection
import dev.intentapp.android.terminals.TerminalState
import dev.intentapp.android.terminals.WorkspaceTerminalsHandle
import dev.intentapp.android.terminals.workspaceTerminalsSection
import dev.intentapp.android.testing.IntentTestTheme
import dev.intentapp.android.testing.createIntentComposeRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** iOS WorkspaceDetailResources: browsers and scripts that have run follow the agent rows. */
class WorkspaceAgentResourcesTest {
    @get:Rule val compose = createIntentComposeRule()
    private val dark = mutableStateOf(false)
    private val calls = mutableListOf<String>()
    private val scriptActions = WorkspaceScriptsActions(
        launch = { calls += "launch:$it" },
        stop = { calls += "stop:$it" },
        remove = { calls += "remove:$it" },
        showOutput = { calls += "output:$it" },
        reload = { calls += "reload" }
    )

    @Test
    fun agentsPageListsBrowsersAndScriptsThatRan() {
        show(agents = listOf(Agent("agent", "Ship the Play release")), browsers = browsers())
        compose.onNodeWithText("Ship the Play release").assertIsDisplayed()
        compose.onNodeWithText("TERMINALS").assertIsDisplayed()
        compose.onNodeWithText("Dev shell").assertIsDisplayed()
        compose.onNodeWithText("BROWSERS").assertIsDisplayed()
        compose.onNodeWithText("Vite dev server").assertIsDisplayed()
        compose.onNodeWithText("SCRIPTS").assertIsDisplayed()
        compose.onNodeWithText("Dev server").assertIsDisplayed()
        compose.onNodeWithText("Build").assertIsDisplayed()
        compose.onNodeWithText("Storybook").assertDoesNotExist()
        compose.onNodeWithText("lint").assertDoesNotExist()
        screenshot("agents-resources-light")
        compose.runOnIdle { dark.value = true }
        screenshot("agents-resources-dark")

        compose.onNodeWithText("Dev shell").performClick()
        compose.onNodeWithText("Vite dev server").performClick()
        compose.onNodeWithText("Build").performClick()
        assertEquals(listOf("terminal:t1", "open:browser:a", "launch:build"), calls)
    }

    @Test
    fun resourcesReplaceTheEmptyAgentsMessage() {
        show(agents = emptyList(), browsers = browsers())
        compose.onNodeWithText("No agents yet").assertDoesNotExist()
        compose.onNodeWithText("BROWSERS").assertIsDisplayed()
        screenshot("agents-resources-only-light")
    }

    @Test
    fun sectionsStayHiddenUntilSomethingExists() {
        show(
            agents = listOf(Agent("agent", "Ship the Play release")),
            browsers = emptyList(),
            scripts = scripts().copy(scripts = emptyList()),
            terminals = emptyList()
        )
        compose.onNodeWithText("Ship the Play release").assertIsDisplayed()
        compose.onNodeWithText("TERMINALS").assertDoesNotExist()
        compose.onNodeWithText("BROWSERS").assertDoesNotExist()
        compose.onNodeWithText("SCRIPTS").assertDoesNotExist()
    }

    private fun show(
        agents: List<Agent>,
        browsers: List<SavedBrowser>,
        scripts: WorkspaceScriptsState = scripts(),
        terminals: List<TerminalInfo> = listOf(TerminalInfo("t1", "Dev shell"))
    ) {
        val terminalHandle = WorkspaceTerminalsHandle(
            TerminalState(terminals, loading = false, connected = true),
            open = { calls += "terminal:$it" },
            rename = {},
            delete = {},
            refresh = {}
        )
        val handle = WorkspaceBrowsersHandle(
            SavedBrowsersState(browsers, loading = false),
            connected = true,
            open = { calls += "open:${it.id}" },
            remove = { calls += "remove-browser:$it" },
            retry = {},
            new = {}
        )
        val resources = AgentListResources(
            visible = terminals.isNotEmpty() ||
                browsers.isNotEmpty() ||
                scripts.rows.any { it.hasRun },
            items = {
                val bleed = Modifier.horizontalBleed(IntentSpacing.page)
                workspaceTerminalsSection(terminalHandle, null, {}, bleed)
                workspaceBrowsersSection(handle, null, {}, bleed)
                workspaceScriptsSection(scripts, scriptActions, null, {}, bleed)
            }
        )
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) {
                Surface(Modifier.fillMaxSize()) {
                    AgentListScreen(
                        AgentListState(agents, isLoading = false),
                        onRefresh = {},
                        onAgentSelected = {},
                        resources = resources
                    )
                }
            }
        }
    }

    private fun browsers() =
        listOf(SavedBrowser("browser:a", "Vite dev server", "http://localhost:5173"))

    private fun scripts() = WorkspaceScriptsState(
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
                "./gradlew assembleDebug",
                "command",
                WorkspaceScriptRuntime("idle", previouslyRunning = true)
            ),
            WorkspaceScriptResource(
                "storybook",
                "workspace",
                "Storybook",
                "pnpm run storybook",
                "service",
                WorkspaceScriptRuntime("idle")
            )
        ),
        discovered = WorkspaceScriptDiscovery.scripts(
            "workspace",
            mapOf("package.json" to """{"scripts":{"lint":"eslint ."}}"""),
            setOf("package.json")
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
