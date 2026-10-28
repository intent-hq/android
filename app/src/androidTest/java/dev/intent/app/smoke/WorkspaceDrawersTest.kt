package dev.intent.app.smoke

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.connection.ConnectionController
import dev.intent.app.connection.ConnectionRepository
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.SavedConnection
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.design.IntentTheme
import dev.intent.app.history.AgentHistoryStore
import dev.intent.app.history.AgentVisit
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceDrawersTest {
    @get:Rule val compose = createIntentComposeRule()
    private lateinit var dispatcher: OnBackPressedDispatcher
    private val servers = java.util.concurrent.ConcurrentHashMap<String, SmokeServer>()

    @Test
    fun contentSwipesOpenAndCloseBothDrawers() = connected {
        contentSwipe(right = true)
        compose.onNodeWithTag("drawer.Connections").assertIsDisplayed()
        screenshot("connections-drawer")
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        compose.onNodeWithTag("drawer.Connections").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
        contentSwipe(right = false)
        compose.onNodeWithTag("drawer.Agent history").assertIsDisplayed()
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
        compose.onNodeWithTag("drawer.Agent history").performTouchInput { swipeRight() }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
    }

    @Test
    fun buttonsBackAndPredictiveBackKeepOnlyOneDrawerOpen() = connected {
        compose.onNodeWithContentDescription("Connections").performClick()
        compose.onNodeWithTag("drawer.Connections").assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
        compose.onNodeWithContentDescription("Agent history").performClick()
        compose.onNodeWithTag("drawer.Agent history").assertIsDisplayed()
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(80f, 200f, 0.5f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.dispatchOnBackCancelled()
        }
        compose.onNodeWithTag("drawer.Agent history").assertIsDisplayed()
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(160f, 200f, 1f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.onBackPressed()
        }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
    }

    @Test
    fun rowSwipeDoesNotOpenDrawerAndVisitedAgentAppearsInHistory() = connected {
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Archive").assertIsDisplayed()
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { swipeRight() }
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { swipeRight() }
        waitFor("Message")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Agent history").performClick()
        compose.onNodeWithTag("history.first.agent").assertIsDisplayed().performClick()
        waitFor("Message")
        assertTrue(servers.getValue("first").requests.any { it.method == "agent.getConversation" })
    }

    @Test
    fun historySwitchesConnectionAndOpensExactConversation() =
        connected(seed = true) { controller ->
            compose.onNodeWithContentDescription("Agent history").performClick()
            compose.onNodeWithTag("history.second.agent").assertIsDisplayed()
            val name = compose.onNodeWithText(
                "Remote agent",
                useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            val context = compose.onNodeWithText(
                "Remote workspace",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            assertTrue(name.top < context.top)
            screenshot("agent-history-drawer")
            compose.onNodeWithTag("history.second.agent").performClick()
            waitFor("Message")
            compose.waitUntil(TIMEOUT) { controller.state.value.activeId == "second" }
            assertTrue(
                servers.getValue("second").requests.any {
                    it.method == "agent.getConversation" && it.params.toString().contains("agent")
                }
            )
            assertEquals("second", controller.state.value.activeId)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Connections").performClick()
            compose.onNodeWithTag("drawer.connection.first").performClick()
            waitFor("Workspaces")
            compose.waitUntil(TIMEOUT) { controller.state.value.activeId == "first" }
        }

    @Test
    fun clearHistoryAndScrimClose() = connected(seed = true) {
        compose.onNodeWithContentDescription("Agent history").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Agents you open on any device appear here.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Agent history").performClick()
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        compose.onNodeWithContentDescription("Connections").performClick()
        compose.onNodeWithTag("drawer.connection.first").performClick()
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
        assertEquals(1, servers.size)
    }

    @Test
    fun swipesStartingAtSystemEdgesDoNotOpenDrawers() = connected {
        compose.onNodeWithTag("workspace.drawers").performTouchInput {
            swipe(Offset(0f, height * 0.85f), Offset(width * 0.8f, height * 0.85f))
        }
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
        compose.onNodeWithTag("workspace.drawers").performTouchInput {
            swipe(Offset(width - 1f, height * 0.85f), Offset(width * 0.2f, height * 0.85f))
        }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
    }

    @Test
    fun conversationSwipeButtonBackAndCrossConnectionHistory() =
        connected(seed = true) { controller ->
            openConversation()
            compose.onNodeWithTag("transcript").performTouchInput {
                swipe(Offset(width * 0.8f, height * 0.8f), Offset(width * 0.1f, height * 0.8f))
            }
            compose.onNodeWithTag("drawer.Agent history").assertIsDisplayed()
            compose.onNodeWithTag("history.first.agent").assertIsSelected()
            compose.onNodeWithText("Current agent").assertIsDisplayed()
            screenshot("conversation-history-light")
            compose.runOnIdle { dispatcher.onBackPressed() }
            compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
            compose.onNodeWithText("Message").assertIsDisplayed()
            compose.onNodeWithContentDescription("Agent history").performClick()
            compose.onNodeWithTag("history.second.agent").performClick()
            waitFor("Message")
            compose.waitUntil(TIMEOUT) { controller.state.value.activeId == "second" }
            compose.onNodeWithContentDescription("Agent history").performClick()
            compose.onNodeWithTag("history.second.agent").assertIsSelected()
            compose.onNodeWithTag("history.first.agent").performClick()
            compose.waitUntil(TIMEOUT) { controller.state.value.activeId == "first" }
            waitFor("Message")
        }

    @Test
    fun conversationHistoryAndConnectionsDarkAppearance() = connected(seed = true, dark = true) {
        compose.onNodeWithContentDescription("Connections").performClick()
        compose.onNodeWithTag("drawer.connection.first").assertIsSelected()
        screenshot("connections-drawer-dark")
        compose.runOnIdle { dispatcher.onBackPressed() }
        openConversation()
        compose.onNodeWithContentDescription("Agent history").performClick()
        screenshot("conversation-history-dark")
    }

    @Test
    fun transcriptHorizontalContentAndSystemEdgesKeepDrawerClosed() = connected {
        openConversation()
        compose.onNodeWithText(WIDE_CODE, substring = true).performTouchInput { swipeLeft() }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        compose.onNodeWithText(WIDE_CODE, substring = true).performTouchInput { swipeRight() }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        val transcript = compose.onNodeWithTag("transcript").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val swipeY = transcript.center.y - root.top
        // Transcript padding is content, not the system edge. Start at the screen boundary.
        compose.onRoot().performTouchInput {
            swipe(Offset(width - 1f, swipeY), Offset(width * 0.1f, swipeY))
        }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        compose.onRoot().performTouchInput {
            swipe(Offset(0f, swipeY), Offset(width * 0.9f, swipeY))
        }
        compose.onNodeWithTag("drawer.Agent history").assertDoesNotExist()
        compose.onNodeWithTag("drawer.Connections").assertDoesNotExist()
    }

    @Test
    fun repeatedHistorySelectionAfterManualNavigationOpensTheAgent() = connected {
        openConversation()
        compose.onNodeWithContentDescription("Agent history").performClick()
        compose.onNodeWithTag("history.first.agent").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Other agent").performClick()
        waitFor("Message")
        compose.onNodeWithContentDescription("Agent history").performClick()
        compose.onNodeWithTag("history.first.other").assertIsSelected()
        compose.onNodeWithTag("history.first.agent").performClick()
        compose.onNodeWithContentDescription("Agent history").performClick()
        compose.onNodeWithTag("history.first.agent").assertIsSelected()
    }

    private fun openConversation() {
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { swipeRight() }
        waitFor("Message")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        directory.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun contentSwipe(right: Boolean) {
        compose.onNodeWithTag("workspace.drawers").performTouchInput {
            val from = if (right) 0.2f else 0.8f
            val to = if (right) 0.9f else 0.1f
            swipe(Offset(width * from, height * 0.85f), Offset(width * to, height * 0.85f))
        }
    }

    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodes(
            hasText(text)
        ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun connected(
        seed: Boolean = false,
        dark: Boolean = false,
        test: (ConnectionController) -> Unit
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(
            "workspace-sections",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        val history = AgentHistoryStore.open(context)
        history.clear()
        if (seed) {
            history.record(
                AgentVisit("second", "workspace", "agent", "Remote agent", "Remote workspace")
            )
        }
        val repository = DrawerConnections()
        ConnectionController(repository, { connection, callback ->
            createConnectionSession(connection, callback, transport = {
                SmokeServer(it, responses).also { server -> servers[connection.id] = server }
            })
        }).use { controller ->
            controller.restore()
            compose.setContent {
                dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                IntentTheme(darkTheme = dark) { ConnectionRoot(controller, {}) }
            }
            waitFor("Android smoke workspace")
            test(controller)
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val WIDE_CODE = "This is a wide code block with a long line " +
            "that must scroll horizontally without opening history."
        val transcript = """[{"id":"message","role":"assistant",
            "contentBlocks":[{"type":"text","text":"```text\n$WIDE_CODE\n```"}]}]"""
        val responses = mapOf(
            "agent.list" to """{"agents":[{"id":"agent","name":"Remote agent"},
                {"id":"other","name":"Other agent"}]}""",
            "agent.get" to """{"agent":{"id":"agent","name":"Remote agent"}}""",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "agent.getConversation" to """{"agentId":"agent","messages":$transcript}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to """{"subscriptionId":"chat","kind":"snapshot","seq":0,
                "snapshot":{"agentId":"agent","messages":$transcript}}""",
            "chat.unsubscribe" to "{}"
        )
    }
}

private class DrawerConnections : ConnectionRepository {
    private var active: String? = "first"
    private val connections = listOf("first", "second").mapIndexed { index, id ->
        SavedConnection(
            id,
            PairingPayload(
                listOf("$id.test"),
                443,
                "test",
                (
                    if (index ==
                        0
                    ) {
                        "AB"
                    } else {
                        "CD"
                    }
                    ).repeat(32)
            )
        )
    }
    override fun list(): List<SavedConnection> = connections
    override fun activeId(): String? = active
    override fun select(id: String?) {
        active = id
    }
    override fun add(payload: PairingPayload): SavedConnection = error("Not used")
    override fun remove(id: String) = Unit
}
