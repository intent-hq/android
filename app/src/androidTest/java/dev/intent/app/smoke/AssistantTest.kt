package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.assistant.ActionProposal
import dev.intent.app.assistant.AssistantRoute
import dev.intent.app.assistant.AssistantScreen
import dev.intent.app.assistant.AssistantStore
import dev.intent.app.assistant.LocalProposalMessage
import dev.intent.app.assistant.LocalProposalStore
import dev.intent.app.assistant.ProposalCard
import dev.intent.app.assistant.ProposalStore
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.WssTransport
import dev.intent.core.protocol.models.WireJson
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import dev.intent.core.protocol.session.SessionTransport
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun newThreadHistorySelectionAndConfirmedDelete() {
        withSession { session, server ->
            val store = AssistantStore({ method, params -> session.request(method, params) })
            store.use {
                compose.setContent {
                    IntentTheme(darkTheme = true) {
                        Surface(Modifier.fillMaxSize()) {
                            AssistantScreen(store, {}) { Text("Conversation $it") }
                        }
                    }
                }
                waitText("Start a conversation")
                screenshot("assistant-empty")
                compose.onNodeWithText("Start a conversation").performClick()
                waitText("Conversation thread-1")
                action("New conversation")
                waitText("Conversation thread-2")
                action("Assistant history")
                waitText("Assistant 2 · Selected")
                screenshot("assistant-history")
                compose.onNodeWithText("Assistant 1").performClick()
                waitText("Conversation thread-1")
                action("Assistant history")
                compose.onNodeWithContentDescription("Delete Assistant 1").performClick()
                waitText("Delete conversation?")
                screenshot("assistant-delete")
                compose.onNodeWithText("Cancel").performClick()
                assertFalse(server.calls.contains("agent.delete"))
                compose.onNodeWithContentDescription("Delete Assistant 1").performClick()
                compose.onNodeWithText("Delete conversation").performClick()
                waitText("Assistant 2 · Selected")
                assertEquals(1, server.calls.count { it == "agent.delete" })
                assertEquals("__chief__", server.lastWorkspace)
            }
        }
    }

    @Test fun proposalApplyUsesPendingMessageAndShowsOutcome() {
        withProposal { server ->
            waitText("Apply")
            screenshot("assistant-proposal")
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(10_000) { server.outcome == "applied" }
            waitText("Applied", unique = false)
            screenshot("assistant-applied")
            assertEquals(1, server.calls.count { it == "workspace.archive" })
            assertEquals(1, server.calls.count { it == "agent.resolveProposal" })
        }
    }

    @Test fun proposalDismissRequiresConfirmationAndDoesNotApply() {
        withProposal { server ->
            waitText("Dismiss")
            compose.onNodeWithText("Dismiss").performClick()
            waitText("Dismiss this proposal?")
            compose.onNodeWithText("Cancel").performClick()
            assertFalse(server.calls.contains("agent.resolveProposal"))
            compose.onNodeWithText("Dismiss").performClick()
            compose.onNodeWithText("Dismiss proposal").performClick()
            waitText("Dismissed")
            screenshot("assistant-dismissed")
            assertFalse(server.calls.contains("workspace.archive"))
        }
    }

    @Test fun assistantConversationRendersProposalAndSharedComposer() {
        withSession { session, server ->
            compose.setContent {
                IntentTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize()) {
                        AssistantRoute(session, "assistant-test", {}, {})
                    }
                }
            }
            waitText("Start a conversation")
            compose.onNodeWithText("Start a conversation").performClick()
            waitText("Archive completed work")
            waitText("Apply")
            screenshot("assistant-conversation")
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(10_000) { server.outcome == "applied" }
            assertEquals("workspace", server.archiveWorkspace)
        }
    }

    private fun withProposal(test: (AssistantServer) -> Unit) = withSession { session, server ->
        ProposalStore({ method, params ->
            session.request(method, params)
        }, "__chief__", "thread").use { store ->
            val proposal =
                ActionProposal(
                    "bulk-op",
                    obj("""{"operation":"workspace.bulkArchive","ids":["workspace"]}"""),
                    obj(
                        """{"title":"Archive completed work","summary":"Archive the finished workspace.","bulkItems":[{"id":"workspace","title":"Completed project"}]}"""
                    ),
                    "proposal"
                )
            compose.setContent {
                IntentTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalProposalStore provides store,
                            LocalProposalMessage provides "message"
                        ) {
                            androidx.compose.foundation.layout.Column { ProposalCard(proposal) }
                        }
                    }
                }
            }
            test(server)
        }
    }

    private fun withSession(test: (RpcSession, AssistantServer) -> Unit) {
        val server = AssistantServer()
        val ready = AtomicBoolean()
        RpcSession(server::transport, obj("{}"), onState = {
            ready.set(it == SessionState.READY)
        }).use { session ->
            session.connect()
            compose.waitUntil(10_000) { ready.get() }
            test(session, server)
        }
    }

    private fun action(label: String) {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText(label).performClick()
    }
    private fun waitText(text: String, unique: Boolean = true) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        if (unique) compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

private class AssistantServer {
    val calls = CopyOnWriteArrayList<String>()

    @Volatile var outcome: String? = null

    @Volatile var lastWorkspace: String? = null
    private val threads = mutableListOf<String>()
    private var count = 0

    @Volatile var archiveWorkspace: String? = null
    private var leaseCount = 0
    fun transport(listener: WssTransport.Listener): SessionTransport = object : SessionTransport {
        override fun connect() = listener.onOpen()
        override fun close() = Unit
        override fun send(message: JsonRpcMessage): Boolean {
            val request = message as? JsonRpcMessage.Request ?: return true
            calls += request.method
            val params = request.params as? JsonObject
            lastWorkspace = params?.get("workspaceId")?.jsonPrimitive?.content ?: lastWorkspace
            val result = when (request.method) {
                "principal.me" -> """{"id":"owner","isAdministrator":true}"""
                "agent.list" -> """{"agents":[${threads.joinToString(",")}]}"""
                "agent.create" -> {
                    count++
                    val agent =
                        """{"id":"thread-$count","name":"Assistant $count","workspaceId":"__chief__"}"""
                    threads.add(0, agent)
                    """{"agent":$agent}"""
                }
                "agent.delete" -> {
                    val id = params?.get("agentId")?.jsonPrimitive?.content
                    threads.removeAll { obj(it)["id"]?.jsonPrimitive?.content == id }
                    """{"success":true}"""
                }
                "agent.get" -> if (outcome ==
                    null
                ) {
                    """{"agent":{"id":"thread-1","name":"Assistant","metadata":{"pendingProposals":[{"proposalId":"proposal","messageId":"message"}]}}}"""
                } else {
                    """{"agent":{"id":"thread-1","name":"Assistant","metadata":{"proposalResolutions":{"proposal":"$outcome"}}}}"""
                }
                "workspace.archive" -> {
                    archiveWorkspace = params?.get("workspaceId")?.jsonPrimitive?.content
                    """{"success":true}"""
                }
                "agent.getQueue" -> """{"success":true,"queue":[]}"""
                "agent.getConversation" -> page()
                "events.subscribe", "chat.subscribe" -> {
                    leaseCount++
                    """{"subscriptionId":"lease-$leaseCount"}"""
                }
                "agent.resolveProposal" -> {
                    outcome = params?.get("outcome")?.jsonPrimitive?.content
                    """{"success":true,"outcome":"$outcome"}"""
                }
                else -> "{}"
            }
            listener.onMessage(
                JsonRpcMessage.Success(request.id, WireJson.parseToJsonElement(result))
            )
            if (request.method == "chat.subscribe") {
                listener.onMessage(
                    JsonRpcMessage.Notification(
                        "subscription.push",
                        obj(
                            """{"subscriptionId":"lease-$leaseCount","seq":0,"kind":"snapshot","snapshot":${page()}}"""
                        )
                    )
                )
            }
            return true
        }
    }
    private fun page(): String {
        val proposal = kotlinx.serialization.json.JsonPrimitive(
            """{"kind":"bulk-op","applyToolCallId":"proposal","payload":{"operation":"workspace.bulkArchive","ids":["workspace"]},"preview":{"title":"Archive completed work","bulkItems":[{"id":"workspace","title":"Completed project"}]}}"""
        )
        val result =
            """{"agentId":"thread-1","messages":[{"id":"message","role":"assistant","contentBlocks":[{"type":"resource","resource":{"mimeType":"application/vnd.intent.proposal+json","text":$proposal}}]}]}"""
        return result
    }
}

private fun obj(value: String) = WireJson.parseToJsonElement(value) as JsonObject
