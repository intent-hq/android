package dev.intent.app.smoke

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.intent.app.connection.ConnectionController
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.pairing.PairingPayload
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSectionsTest {
    @get:Rule val compose = createIntentComposeRule()
    private val dark = mutableStateOf(false)

    @Test
    fun sectionsKeepSearchScrollAndSelectionAcrossRotationAndTheme() = connected {
        openWorkspace()
        compose.onNodeWithText("Agents").assertIsSelected()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Search agents"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search agents").performClick()
        compose.onNodeWithText("Search agents").performTextInput("Agent")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Agent 35"))
        compose.onNodeWithText("Agent 35").assertIsDisplayed()
        val agentAnchor = firstVisibleRow("Agent ")
        compose.onNodeWithText("Context").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription("Search notes and tasks")
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search notes and tasks").performClick()
        compose.onNodeWithText("Search notes and tasks").performTextInput("Plan")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Plan 35"))
        compose.onNodeWithText("Plan 35").assertIsDisplayed()
        val noteAnchor = firstVisibleRow("Plan ")
        compose.onNodeWithText("Changes").performClick()
        compose.onNodeWithText("Changes").assertIsSelected()
        waitForText("Commit history")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Commit 35"))
        val commitAnchor = firstVisibleRow("Commit ")
        rotate()
        compose.recreateActivity()
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("Changes").assertIsSelected()
        waitForVisibleText(commitAnchor)
        compose.onNodeWithText(commitAnchor).assertIsDisplayed()
        compose.onNodeWithText("Context").performClick()
        waitForVisibleText(noteAnchor)
        compose.onNodeWithText(noteAnchor).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).assertTextContains("Plan")
        compose.onNodeWithText("Agents").performClick()
        waitForVisibleText(agentAnchor)
        compose.onNodeWithText(agentAnchor).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNode(hasSetTextAction()).assertTextContains("Agent")
        back()
        compose.onNodeWithText("Workspaces").assertIsDisplayed()
    }

    @Test
    fun childrenReturnToTheirSectionAndSectionBackExitsWorkspace() = connected {
        openWorkspace()
        compose.onNodeWithText("Agent 1").performClick()
        compose.onNodeWithText("Message").assertIsDisplayed()
        compose.onNodeWithText("Linked plan").performTouchInput { click(Offset(20f, center.y)) }
        compose.onNodeWithText("Note body").assertIsDisplayed()
        back()
        compose.onNodeWithText("Message").assertIsDisplayed()
        compose.onNodeWithText("Linked plan").assertIsDisplayed()
        back()
        compose.onNodeWithText("Agents").assertIsSelected()
        compose.onNodeWithText("Context").performClick()
        waitForText("Plan 1")
        compose.onNodeWithText("Plan 1").performClick()
        compose.onNodeWithText("Note body").assertIsDisplayed()
        back()
        compose.onNodeWithText("Context").assertIsSelected()
        compose.onNodeWithText("Changes").performClick()
        compose.onNodeWithText("M  app.kt").performClick()
        compose.onNodeWithText("Unstaged diff").assertIsDisplayed()
        back()
        compose.onNodeWithText("Changes").assertIsSelected()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Terminals").performClick()
        compose.onNodeWithText("No terminal sessions").assertIsDisplayed()
        back()
        compose.onNodeWithText("Changes").assertIsSelected()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Workspaces").assertIsDisplayed()
        compose.onNodeWithText("Android smoke workspace").performClick()
        compose.onNodeWithText("Changes").assertIsSelected()
        compose.onNodeWithText("Context").performClick()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Workspaces").assertIsDisplayed()
    }

    @Test
    fun notificationOpensAgentsWithSectionSwitcher() = connected(notification = true) {
        compose.onNodeWithText("Agents").assertIsSelected()
        compose.onNodeWithText("Context").assertIsDisplayed()
        compose.onNodeWithText("Changes").assertIsDisplayed()
        back()
        compose.onNodeWithText("Workspaces").assertIsDisplayed()
    }

    @Test
    fun menusOnlyContainActionsForTheCurrentPage() = connected {
        screenshot("workspaces-header")
        menu(
            listOf("Show archived"),
            listOf("Settings", "Connections", "Disconnect", "Create workspace")
        )
        openWorkspace()
        screenshot("agents-header")
        menu(
            listOf("New agent", "Transfer workspace", "Rename workspace"),
            listOf("Settings", "Connections", "Disconnect")
        )
        compose.onNodeWithText("Agent 1").performClick()
        screenshot("conversation-header")
        menu(
            listOf("Model settings", "Rename agent", "Delete agent"),
            listOf("Transfer workspace", "Settings", "Connections", "Terminals")
        )
        back()
        compose.onNodeWithText("Context").performClick()
        screenshot("context-header")
        menu(
            listOf("Transfer workspace", "Rename workspace"),
            listOf("Settings", "Disconnect", "Model settings")
        )
        compose.onNodeWithText("Plan 1").performClick()
        screenshot("note-header")
        menu(listOf("Refresh", "Comments"), listOf("Transfer workspace", "Settings", "Connections"))
        back()
        compose.onNodeWithText("Changes").performClick()
        screenshot("changes-header")
        menu(
            listOf("Transfer workspace", "Rename workspace"),
            listOf("Settings", "Disconnect", "Model settings")
        )
        compose.onNodeWithText("M  app.kt").performClick()
        screenshot("diff-header")
        menu(listOf("Refresh diff"), listOf("Transfer workspace", "Settings", "Connections"))
        back()
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Terminals").performClick()
        screenshot("terminals-header")
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
    }

    @Test
    fun sessionNoteOpensCommentsAndReturnsWithoutParentActions() = connected {
        openWorkspace()
        compose.onNodeWithText("Context").performClick()
        waitForText("Plan 1")
        compose.onNodeWithText("Plan 1").performClick()
        compose.onNodeWithText("Note body").assertIsDisplayed()
        menu(
            listOf("Refresh", "Comments"),
            listOf("Transfer workspace", "Settings", "Connections", "Disconnect")
        )
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Comments").performClick()
        waitForText("No comment threads for this note.")
        compose.onNodeWithText("Comments").assertIsDisplayed()
        compose.onNodeWithContentDescription("More actions").assertDoesNotExist()
        screenshot("note-comments-route")
        back()
        compose.onNodeWithText("Note body").assertIsDisplayed()
        menu(
            listOf("Refresh", "Comments"),
            listOf("Transfer workspace", "Settings", "Connections")
        )
    }

    private fun menu(present: List<String>, absent: List<String>) {
        compose.onNodeWithContentDescription("More actions").performClick()
        present.forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        absent.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
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

    private fun firstVisibleRow(prefix: String): String {
        val rows = compose.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0 }
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull {
                    it.text.startsWith(prefix) && it.text.removePrefix(prefix).toIntOrNull() != null
                }?.let { node.boundsInRoot.top to it.text }
            }
        return rows.minBy { it.first }.second
    }

    private fun waitForVisibleText(text: String) {
        compose.waitUntil(TIMEOUT) {
            runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openWorkspace() {
        compose.onNodeWithText("Android smoke workspace").performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Agent 1")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    private fun rotate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .single().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitForIdle()
    }

    private fun connected(notification: Boolean = false, test: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(
                "workspace-sections",
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        val repository = MemoryConnections()
        val saved = repository.add(
            PairingPayload(listOf("desktop.test"), 443, "smoke-token", "AB".repeat(32))
        )
        repository.select(saved.id)
        ConnectionController(repository, { connection, callback ->
            createConnectionSession(connection, callback, transport = {
                SmokeServer(it, responses)
            })
        }).use { controller ->
            controller.restore()
            compose.setContent {
                IntentTheme(dark.value) {
                    ConnectionRoot(
                        controller,
                        {},
                        notificationWorkspace = if (notification) saved.id to "workspace" else null
                    )
                }
            }
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(hasText("Android smoke workspace"))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            test()
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        val responses = mapOf(
            "agent.list" to """{"agents":[${(1..40).joinToString {
                """{"id":"agent-$it","name":"Agent $it"}"""
            }}]}""",
            "note.list" to """{"notes":[${(1..40).joinToString {
                """{"id":"note-$it","title":"Plan $it"}"""
            }}]}""",
            "note.get" to
                """{"note":{"id":"note-1","title":"Plan 1","content":"Note body"}}""",
            "note.listTasks" to "[]",
            "comment.list" to """{"threads":[],"totalThreads":0,"totalComments":0}""",
            "comment.subscribe" to """{"subscriptionId":"comments"}""",
            "comment.unsubscribe" to "{}",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "agent.get" to """{"agent":{"id":"agent-1","name":"Agent 1"}}""",
            "agent.getConversation" to """{"agentId":"agent-1","messages":[
                {"id":"message-1","role":"assistant","contentBlocks":[
                {"type":"text","text":"[Linked plan](intent://local/note/note-1)"}]}]}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to """{"subscriptionId":"chat","kind":"snapshot","seq":0,
                "snapshot":{"agentId":"agent-1","messages":[
                {"id":"message-1","role":"assistant","contentBlocks":[
                {"type":"text","text":"[Linked plan](intent://local/note/note-1)"}]}]}}""",
            "chat.unsubscribe" to "{}",
            "git.status" to """{"branch":"main","files":[
                {"path":"app.kt","status":"M","staged":false}]}""",
            "file-tracking.loadCommits" to """{"commits":[${(1..40).joinToString {
                """{"hash":"commit-$it","message":"Commit $it"}"""
            }}]}""",
            "git.diffs" to "[]",
            "terminal.list" to """{"terminals":[],"daemonBootId":"smoke-boot"}"""
        )
    }
}
