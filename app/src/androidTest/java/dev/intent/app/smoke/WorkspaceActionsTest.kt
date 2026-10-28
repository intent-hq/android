package dev.intent.app.smoke

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.connection.ConnectionController
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceActionsTest {
    @get:Rule val compose = createIntentComposeRule()
    private lateinit var server: SmokeServer
    private val fixtureResponses = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Test
    fun renameCancelAndSaveFromHeader() = connected {
        openWorkspace()
        menu("Rename workspace")
        compose.onNodeWithText("Workspace name").performTextReplacement("Discarded")
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(server.requests.none { it.method == "workspace.update" })
        menu("Rename workspace")
        compose.onNodeWithText("Workspace name").performTextReplacement(" Renamed ")
        fixtureResponses["workspace.list"] =
            """{"workspaces":[{"id":"workspace","title":"Renamed"}]}"""
        compose.onNodeWithText("Save").performClick()
        waitFor("Renamed")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Renamed").assertIsDisplayed()
        assertEquals(
            """{"workspaceId":"workspace","title":"Renamed"}""",
            server.requests.single { it.method == "workspace.update" }.params.toString()
        )
    }

    @Test
    fun archiveCancelThenConfirmReturnsToListAndShowArchivedRestoresRow() = connected {
        openWorkspace()
        menu("Archive workspace")
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(server.requests.none { it.method == "workspace.archive" })
        menu("Archive workspace")
        fixtureResponses["workspace.list"] =
            """{"workspaces":[{"id":"workspace","title":"$TITLE","status":"Archived"}]}"""
        compose.onNodeWithText("Archive", substring = false).performClick()
        waitFor("Workspaces")
        compose.onNodeWithText(TITLE).assertDoesNotExist()
        menu("Show archived")
        compose.onNodeWithText(TITLE).assertIsDisplayed()
        compose.onNodeWithText("Archived").assertIsDisplayed()
    }

    @Test
    fun leftSwipeRevealsActionsAndDeleteRequiresConfirmation() = connected {
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { swipeLeft() }
        compose.onNodeWithText(TITLE).assertIsDisplayed()
        screenshot("workspace-swipe-actions")
        compose.onNodeWithText("Delete").performClick()
        assertTrue(server.requests.none { it.method == "workspace.delete" })
        fixtureResponses["workspace.list"] = """{"workspaces":[]}"""
        compose.onNodeWithText("Delete", substring = false).performClick()
        waitFor("No workspaces yet")
        assertEquals(1, server.requests.count { it.method == "workspace.delete" })
    }

    @Test
    fun longPressOpensMenuAndFailedDeleteKeepsRow() = connected(
        mapOf("workspace.delete" to """{"success":false}""")
    ) {
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { longClick() }
        compose.onNodeWithText("Open most recent agent").assertIsDisplayed()
        compose.onNodeWithText("Archive workspace").assertIsDisplayed()
        screenshot("workspace-destructive-menu")
        compose.onNodeWithText("Delete workspace").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        waitFor("The server did not confirm the workspace change. Try again.")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText(TITLE).assertIsDisplayed()
    }

    @Test
    fun rightSwipeOpensRecentDelegateEvenWhenContextWasSaved() = connected {
        openWorkspace()
        compose.onNodeWithText("Context").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("workspace.row.workspace").performTouchInput { swipeRight() }
        waitFor("Message")
        compose.onNodeWithText("Recent delegate").assertIsDisplayed()
        assertTrue(
            server.requests.any {
                it.method == "agent.getConversation" && it.params.toString().contains("recent")
            }
        )
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

    private fun openWorkspace() {
        compose.onNodeWithText(TITLE).performClick()
        waitFor("Agents")
    }

    private fun menu(label: String) {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun waitFor(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodes(
            hasText(text)
        ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun connected(extra: Map<String, String> = emptyMap(), test: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(
                "workspace-sections",
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        fixtureResponses.putAll(responses + extra)
        val repository = MemoryConnections()
        val saved = repository.add(
            PairingPayload(listOf("desktop.test"), 443, "smoke-token", "AB".repeat(32))
        )
        repository.select(saved.id)
        ConnectionController(repository, { connection, callback ->
            createConnectionSession(connection, callback, transport = {
                SmokeServer(it, fixtureResponses).also { fixture -> server = fixture }
            })
        }).use { controller ->
            controller.restore()
            compose.setContent { IntentTheme { ConnectionRoot(controller, {}) } }
            waitFor(TITLE)
            test()
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val TITLE = "Android smoke workspace"
        val responses = mapOf(
            "workspace.update" to """{"workspace":{"id":"workspace","title":"Renamed"}}""",
            "workspace.archive" to """{"workspace":{"id":"workspace","status":"Archived"}}""",
            "workspace.delete" to """{"success":true}""",
            "agent.list" to """{"agents":[
                {"id":"old","name":"Old agent","lastActivity":"2026-10-26T12:00:00Z"},
                {"id":"recent","name":"Recent delegate","parentAgentId":"old",
                 "lastActivity":"2026-10-26T12:00:00.500Z"},
                {"id":"retired","name":"Retired agent","retiredAt":"now",
                 "lastActivity":"2026-10-27T12:00:00Z"}]}""",
            "agent.get" to """{"agent":{"id":"recent","name":"Recent delegate"}}""",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "agent.getConversation" to """{"agentId":"recent","messages":[]}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to """{"subscriptionId":"chat","kind":"snapshot","seq":0,
                "snapshot":{"agentId":"recent","messages":[]}}""",
            "chat.unsubscribe" to "{}",
            "note.list" to """{"notes":[]}""",
            "note.subscribe" to """{"subscriptionId":"notes"}""",
            "note.unsubscribe" to "{}"
        )
    }
}
