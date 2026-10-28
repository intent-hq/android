package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationSendStore
import dev.intent.app.conversation.RpcConversationCommands
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationControlsTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun stopEditSendNowAndDismissUseScopedCommands() {
        lateinit var server: SmokeServer
        val ready = AtomicBoolean()
        RpcSession(
            { listener -> SmokeServer(listener, responses()).also { server = it } },
            JsonObject(emptyMap()),
            onState = { ready.set(it == SessionState.READY) }
        ).use { session ->
            session.connect()
            compose.waitUntil(TIMEOUT) { ready.get() }
            ConversationSendStore(
                RpcConversationCommands(session, "workspace", "agent"),
                "agent"
            ).use { sends ->
                sends.refresh()
                compose.waitUntil(TIMEOUT) { sends.state.value.questions != null }
                compose.setContent {
                    val state by sends.state.collectAsState()
                    IntentTheme(darkTheme = true) {
                        Surface(Modifier.fillMaxSize()) {
                            ConversationComposer(
                                state,
                                { false },
                                sends::retract,
                                sends::answer,
                                onStop = sends::stopReply,
                                onEditQueued = sends::editQueued,
                                onSendNow = sends::sendNow,
                                onDismissQuestions = sends::dismissQuestions
                            )
                        }
                    }
                }
                screenshot("conversation-controls")
                compose.onNodeWithText("Stop response").performClick()
                compose.waitUntil(TIMEOUT) { server.requests.any { it.method == "agent.stop" } }
                compose.onNodeWithText("Stopping…").assertIsNotEnabled()
                compose.onNodeWithText("Edit").performScrollTo().performClick()
                compose.onNodeWithText(
                    "Edit queued message"
                ).performTextReplacement("Updated queue text")
                screenshot("conversation-queue-edit")
                compose.onNodeWithText("Save").performClick()
                compose.waitUntil(TIMEOUT) {
                    server.requests.any {
                        it.method ==
                            "agent.editQueuedMessage"
                    }
                }
                assertEquals(
                    JsonPrimitive("Updated queue text"),
                    (
                        server.requests.single {
                            it.method == "agent.editQueuedMessage"
                        }.params as JsonObject
                        )["content"]
                )
                compose.onNodeWithText("Send now").performScrollTo().performClick()
                compose.waitUntil(TIMEOUT) {
                    server.requests.any {
                        it.method ==
                            "agent.sendQueuedMessageNow"
                    }
                }
                compose.onNodeWithText("Dismiss questions").performScrollTo().performClick()
                compose.onNodeWithText("Dismiss questions?").assertIsDisplayed()
                screenshot("conversation-dismiss-confirmation")
                compose.onNodeWithText("Cancel").performClick()
                assertEquals(0, server.requests.count { it.method == "agent.dismissQuestions" })
                compose.onNodeWithText("Dismiss questions").performScrollTo().performClick()
                compose.onNodeWithText("Dismiss").performClick()
                compose.waitUntil(TIMEOUT) { sends.state.value.questions == null }
                compose.onNodeWithText("Choose?").assertDoesNotExist()
                val methods =
                    setOf(
                        "agent.stop",
                        "agent.editQueuedMessage",
                        "agent.sendQueuedMessageNow",
                        "agent.dismissQuestions"
                    )
                methods.forEach { method ->
                    val request = server.requests.single { it.method == method }
                    val params = request.params as JsonObject
                    assertEquals(JsonPrimitive("agent"), params["agentId"])
                    assertEquals(JsonPrimitive("workspace"), params["workspaceId"])
                    if (method != "agent.stop") {
                        assertEquals(
                            JsonPrimitive(
                                if (method ==
                                    "agent.dismissQuestions"
                                ) {
                                    "questions"
                                } else {
                                    "queued"
                                }
                            ),
                            params["messageId"]
                        )
                    }
                }
            }
        }
    }

    private fun responses() = mapOf(
        "agent.get" to
            """{"agent":{"id":"agent","name":"Agent","turnInFlight":true,"metadata":{"pendingQuestionsMessageId":"questions"}}}""",
        "agent.getQueue" to
            """{"success":true,"queue":[{"id":"queued","content":"Queued draft","queuedAt":"now","position":0}]}""",
        "agent.getConversation" to
            """{"agentId":"agent","messages":[{"id":"questions","role":"assistant","contentBlocks":[{"type":"resource","resource":{"mimeType":"application/vnd.intent.question+json","text":"{\"attachmentId\":\"q\",\"header\":\"Choice\",\"question\":\"Choose?\",\"options\":[{\"label\":\"One\"},{\"label\":\"Two\"}]}"}}]}]}""",
        "agent.stop" to """{"success":true}""",
        "agent.editQueuedMessage" to """{"success":true}""",
        "agent.sendQueuedMessageNow" to """{"success":true,"queued":true}""",
        "agent.dismissQuestions" to """{"success":true,"dismissedQuestionsMessageId":"questions"}"""
    )

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File)
            ?: File(
                instrumentation.targetContext.externalMediaDirs.first(),
                "additional_test_output"
            )
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name-dark.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

private const val TIMEOUT = 10_000L
