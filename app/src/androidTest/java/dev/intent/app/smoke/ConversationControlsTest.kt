package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.conversation.ComposerDraft
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationSendState
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
                compose.onNodeWithContentDescription("Stop").performClick()
                compose.waitUntil(TIMEOUT) { server.requests.any { it.method == "agent.stop" } }
                compose.onNodeWithContentDescription("Stop").assertIsNotEnabled()
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

    @Test
    fun stopReplacesSendAndPreservesDraftInBothThemes() {
        val dark = mutableStateOf(false)
        val state = mutableStateOf(ConversationSendState(running = true))
        val draft = ComposerDraft().apply { text = "Keep this draft" }
        var stops = 0
        compose.setContent {
            IntentTheme(darkTheme = dark.value) {
                Surface(Modifier.fillMaxSize()) {
                    ConversationComposer(
                        state.value,
                        { false },
                        {},
                        { _, _ -> false },
                        draft = draft,
                        onStop = {
                            stops++
                            state.value = state.value.copy(stopping = true)
                        }
                    )
                }
            }
        }
        listOf(false, true).forEach { darkTheme ->
            compose.runOnIdle {
                dark.value = darkTheme
                state.value = ConversationSendState(running = true)
            }
            val theme = if (darkTheme) "dark" else "light"
            compose.onNodeWithContentDescription("Stop").assertIsDisplayed()
            compose.onNodeWithText("Stop response").assertDoesNotExist()
            compose.onNodeWithText("Send").assertDoesNotExist()
            screenshot("stop-working-$theme", suffix = "")
            compose.onNodeWithContentDescription("Stop").performClick()
            compose.onNodeWithContentDescription("Stop").assertIsNotEnabled().performClick()
            compose.runOnIdle {
                assertEquals(if (darkTheme) 2 else 1, stops)
                assertEquals("Keep this draft", draft.text)
                state.value = ConversationSendState()
            }
            compose.onNodeWithContentDescription("Stop").assertDoesNotExist()
            compose.onNodeWithText("Send").assertIsDisplayed()
            screenshot("stop-idle-$theme", suffix = "")
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

    private fun screenshot(name: String, suffix: String = "-dark") {
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
        File(directory, "$name$suffix.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}

private const val TIMEOUT = 10_000L
