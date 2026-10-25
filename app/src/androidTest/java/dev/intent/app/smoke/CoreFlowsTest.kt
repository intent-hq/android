package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.comments.CommentScreen
import dev.intent.app.comments.CommentState
import dev.intent.app.connection.ConnectionController
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationScreen
import dev.intent.app.conversation.ConversationSendStore
import dev.intent.app.conversation.ConversationState
import dev.intent.app.conversation.RpcConversationCommands
import dev.intent.app.conversation.SendStatus
import dev.intent.app.design.IntentTheme
import dev.intent.app.notes.NoteReaderScreen
import dev.intent.app.notes.NoteReaderState
import dev.intent.app.workspaces.WorkspaceListScreen
import dev.intent.app.workspaces.WorkspaceListState
import dev.intent.core.protocol.models.Comment
import dev.intent.core.protocol.models.CommentThreadSummary
import dev.intent.core.protocol.models.Note
import dev.intent.core.protocol.models.NoteTaskRow
import dev.intent.core.protocol.models.Workspace
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreFlowsTest {
    @get:Rule val compose = createComposeRule()
    private val dark = mutableStateOf(false)

    @Test
    fun manualPairingConnectsToWorkspaceList() {
        val repository = MemoryConnections()
        lateinit var server: SmokeServer
        ConnectionController(repository, { saved, callback ->
            createConnectionSession(saved, callback, transport = { listener ->
                SmokeServer(listener).also { server = it }
            })
        }).use { controller ->
            content { ConnectionRoot(controller, {}) }
            compose.onNodeWithText("Scan QR code").assertIsDisplayed()
            compose.onNodeWithText("Enter connection details").performClick()
            compose.onNodeWithText("Review connection").assertIsNotEnabled()
            compose.onNodeWithText("Host").performTextInput("desktop.test")
            compose.onNodeWithText("Port").performTextInput("443")
            compose.onNodeWithText("Pairing token").performTextInput("smoke-token")
            compose.onNodeWithText("Certificate fingerprint (optional)")
                .performScrollTo().performTextInput("AB".repeat(32))
            screenshots("pairing")
            compose.onNodeWithText("Review connection").performScrollTo().performClick()
            compose.waitUntil(TIMEOUT) { controller.state.value.pending != null }
            compose.onNodeWithText("Trust and connect?").assertIsDisplayed()
            assertTrue(repository.list().isEmpty())
            compose.onNodeWithText("Trust and connect").performClick()
            compose.waitUntil(TIMEOUT) { controller.state.value.session != null }
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Android smoke workspace"))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNodeWithText("Android smoke workspace").assertIsDisplayed()
            assertEquals("smoke-token", repository.list().single().payload.token)
            assertEquals(
                listOf("client.hello", "principal.me"),
                server.requests.take(2).map {
                    it.method
                }
            )
            screenshots("paired-workspaces")
        }
    }

    @Test
    fun workspaceListSelectsWorkspace() {
        var selected: String? = null
        content {
            WorkspaceListScreen(
                WorkspaceListState(
                    listOf(Workspace("workspace", "Android smoke workspace")),
                    false
                ),
                {},
                { selected = it }
            )
        }
        compose.onNodeWithText("Android smoke workspace").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("workspace", selected) }
        screenshots("workspaces")
    }

    @Test
    fun noteReaderDisplaysBodyAndUpdatesTask() {
        val row = mutableStateOf(NoteTaskRow(3, "Check the emulator", "todo"))
        content {
            NoteReaderScreen(
                NoteReaderState(
                    Note("spec", "Smoke plan", content = "Read the project plan."),
                    listOf(row.value),
                    loading = false
                ),
                {},
                onTaskStatus = { task, status -> row.value = task.copy(status = status) }
            )
        }
        compose.onNodeWithText("Smoke plan").assertIsDisplayed()
        compose.onNodeWithText("Read the project plan.").assertIsDisplayed()
        compose.onNodeWithText("Check the emulator").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.runOnIdle { assertEquals("done", row.value.status) }
        screenshots("note-tasks")
    }

    @Test
    fun commentThreadOpensAndAcceptsReply() {
        val selected = mutableStateOf<String?>(null)
        val state = mutableStateOf(CommentState(threads = listOf(thread()), loading = false))
        var reply: String? = null
        content {
            CommentScreen(
                state.value,
                selected.value,
                { selected.value = it },
                {},
                { id, text -> state.value = state.value.copy(drafts = mapOf(id to text)) },
                { id -> reply = state.value.drafts[id] }
            )
        }
        compose.onNodeWithText("Check the emulator").performClick()
        compose.onNodeWithText("Please check both themes.").assertIsDisplayed()
        compose.onNodeWithText("Send reply").assertIsNotEnabled()
        compose.onNodeWithText(
            "Reply"
        ).performScrollTo().performTextInput("Both themes look correct.")
        compose.onNodeWithText("Send reply").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Both themes look correct.", reply) }
        screenshots("comments")
    }

    @Test
    fun conversationSendsMessageThroughSession() {
        lateinit var server: SmokeServer
        val ready = AtomicBoolean()
        val session =
            RpcSession(
                { listener ->
                    SmokeServer(listener).also { server = it }
                },
                JsonObject(emptyMap()),
                onState = { ready.set(it == SessionState.READY) }
            )
        session.use {
            session.connect()
            compose.waitUntil(TIMEOUT) { ready.get() }
            ConversationSendStore(
                RpcConversationCommands(session, "workspace", "agent"),
                "agent"
            ).use { sends ->
                content {
                    val state by sends.state.collectAsState()
                    ConversationScreen(ConversationState(isLoading = false), {}, { _, _ -> }, {}) {
                        ConversationComposer(state, { sends.send(it) }, {}, { _, _ -> false })
                    }
                }
                compose.onNodeWithText("Send").assertIsNotEnabled()
                compose.onNodeWithText("Message").performTextInput("Check the Android build.")
                compose.onNodeWithText("Send").performClick()
                compose.waitUntil(TIMEOUT) {
                    sends.state.value.attempts.singleOrNull()?.status ==
                        SendStatus.ACCEPTED
                }
                val request = server.requests.single { it.method == "agent.sendMessage" }
                assertEquals(
                    JsonPrimitive("Check the Android build."),
                    (request.params as JsonObject)["content"]
                )
                compose.onNodeWithText("Message").assertTextEquals("Message", "")
                compose.onNodeWithText("Accepted. Waiting for the transcript.").assertIsDisplayed()
                screenshots("conversation")
            }
        }
    }

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            IntentTheme(dark.value) { Surface(Modifier.fillMaxSize()) { body() } }
        }
    }

    private fun screenshots(name: String) {
        listOf(false, true).forEach { night ->
            compose.runOnIdle { dark.value = night }
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory =
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?.let(::File)
                    ?: File(
                        instrumentation.targetContext.externalMediaDirs.first(),
                        "additional_test_output"
                    )
            check(directory.mkdirs() || directory.isDirectory)
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory, "$name-${if (night) "dark" else "light"}.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    private fun thread() = CommentThreadSummary(
        "thread", "spec", "open", "2026-10-24", "2026-10-24", "Sam", "user", "2026-10-24", 1,
        targetedText = "Check the emulator",
        comments = listOf(
            Comment(
                "comment", "thread", "comment", "Please check both themes.",
                "Sam", "user", "open", "2026-10-24", "2026-10-24"
            )
        )
    )

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
