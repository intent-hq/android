package dev.intent.app.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.conversation.ComposerDraft
import dev.intent.app.conversation.ConversationComposer
import dev.intent.app.conversation.ConversationScreen
import dev.intent.app.conversation.ConversationSendState
import dev.intent.app.conversation.ConversationState
import dev.intent.app.design.IntentTheme
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.QueuedMessage
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationTranscriptTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun thinkingAndToolsStartCollapsedButTextStaysVisible() {
        val message = ConversationMessage(
            "reply",
            "assistant",
            listOf(
                ContentBlock("thinking", id = "thought", text = "Private reasoning detail"),
                ContentBlock(
                    "tool_use",
                    id = "tool",
                    name = "read_file",
                    input = JsonPrimitive("tool payload")
                ),
                ContentBlock("text", id = "text", text = "Visible answer")
            )
        )
        show(ConversationState(messages = listOf(message), isLoading = false))
        compose.onNodeWithText("Visible answer").assertIsDisplayed()
        compose.onNodeWithText("Private reasoning detail").assertDoesNotExist()
        compose.onNodeWithText("tool payload").assertDoesNotExist()
        compose.onNodeWithText("▸ thinking").performClick()
        compose.onNodeWithText("Private reasoning detail").assertIsDisplayed()
        compose.onNodeWithText("▸ read_file").performClick()
        compose.onNodeWithText("tool payload").assertIsDisplayed()
    }

    @Test
    fun manualScrollPausesIncomingMessagesAndPillReturnsToBottom() {
        val state = mutableStateOf(ConversationState(messages = messages(), isLoading = false))
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(state.value, {}, { _, _ -> }, {})
                }
            }
        }
        compose.onNodeWithText("Message 49").assertIsDisplayed()
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.runOnIdle {
            state.value = state.value.copy(messages = state.value.messages + message(50))
        }
        compose.onNodeWithText("New messages ↓").assertIsDisplayed().performClick()
        compose.onNodeWithText("Message 50").assertIsDisplayed()
        compose.onNodeWithText("New messages ↓").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(messages = state.value.messages + message(51))
        }
        compose.onNodeWithText("Message 51").assertIsDisplayed()
    }

    @Test
    fun topLoadsHistoryOnceAndPullRefreshHasNoButton() {
        var loads = 0
        var refreshes = 0
        show(
            ConversationState(messages = messages(), nextToken = "older", isLoading = false),
            { loads++ },
            { refreshes++ }
        )
        compose.runOnIdle { assertEquals(0, loads) }
        compose.onNodeWithTag("transcript").performScrollToIndex(49)
        compose.runOnIdle { assertEquals(1, loads) }
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
        compose.runOnIdle {
            assertEquals(1, loads)
            assertEquals(1, refreshes)
        }
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.onNodeWithText("Load older messages").assertDoesNotExist()
        compose.onNodeWithText("Pause auto-scroll").assertDoesNotExist()
        compose.onNodeWithText("Check status").assertDoesNotExist()
    }

    @Test
    fun suggestedPromptChipsHideMarkupAndPrefillWithoutSending() {
        val draft = ComposerDraft()
        var sent = false
        val reply = ConversationMessage(
            "suggestions",
            "assistant",
            listOf(
                ContentBlock(
                    "text",
                    text = "Done.\n<!-- suggested-prompts\nRun tests\nReview code\n-->"
                )
            )
        )
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(
                        ConversationState(messages = listOf(reply), isLoading = false),
                        {},
                        { _, _ -> },
                        {},
                        onSuggestedPrompt = { draft.text = it }
                    ) {
                        ConversationComposer(
                            ConversationSendState(),
                            {
                                sent = true
                                true
                            },
                            {},
                            { _, _ -> false },
                            draft = draft
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Done.").assertIsDisplayed()
        compose.onNodeWithText("<!-- suggested-prompts", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Run tests").performClick()
        compose.onNodeWithText("Message").assertTextEquals("Message", "Run tests")
        compose.runOnIdle { assertEquals(false, sent) }
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle { assertEquals(true, sent) }
    }

    @Test
    fun coordinationAndQueuedMachineMessagesStartCollapsed() {
        val machineText = "[MESSAGE FROM AGENT Worker] Internal report"
        val queuedText = "[WORKSPACE EVENTS] Internal wake"
        val rows = listOf(
            ConversationMessage(
                "machine",
                "user",
                listOf(ContentBlock("text", text = machineText))
            ),
            ConversationMessage(
                "human",
                "user",
                listOf(ContentBlock("text", text = "Human message"))
            )
        )
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(ConversationState(messages = rows, isLoading = false), {
                    }, { _, _ -> }, {}) {
                        ConversationComposer(
                            ConversationSendState(
                                queue = listOf(QueuedMessage("q", queuedText, "", 0))
                            ),
                            { true },
                            {},
                            { _, _ -> false }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Human message").assertIsDisplayed()
        compose.onNodeWithText(machineText).assertDoesNotExist()
        compose.onNodeWithText("Queued: $queuedText").assertDoesNotExist()
        compose.onNodeWithText("▸ Agent message").performClick()
        compose.onNodeWithText(machineText).assertIsDisplayed()
        compose.onNodeWithText("▸ 1 queued background updates").performClick()
        compose.onNodeWithText("Queued: $queuedText").assertIsDisplayed()
    }

    @Test
    fun rawMarkupAndRepeatedLifecycleNoticesStayCompact() {
        val rows = listOf(
            ConversationMessage(
                "n1",
                "user",
                listOf(ContentBlock("text", text = "Worker sent a message and finished"))
            ),
            ConversationMessage(
                "n2",
                "user",
                listOf(ContentBlock("text", text = "Worker sent a message and finished"))
            ),
            ConversationMessage(
                "reply",
                "assistant",
                listOf(
                    ContentBlock(
                        "text",
                        text = "Before\n<invoke name=\"read\">payload</invoke>\nAfter"
                    )
                )
            )
        )
        show(ConversationState(messages = rows, isLoading = false))
        compose.onNodeWithText("Before").assertIsDisplayed()
        compose.onNodeWithText("After").assertIsDisplayed()
        compose.onNodeWithText("▸ Agent update · 2 updates").assertIsDisplayed()
        compose.onNodeWithText("Worker sent a message and finished").assertDoesNotExist()
        compose.onNodeWithText("<invoke", substring = true).assertDoesNotExist()
        screenshot("format-compact")
        compose.onNodeWithText("▸ Tool detail").performClick()
        compose.onNodeWithText("<invoke name=\"read\">payload</invoke>").assertIsDisplayed()
        screenshot("format-expanded")
    }

    @Test
    fun queuedMonitorUpdatesUseOneExpandableSummary() {
        val metadata = JsonObject(mapOf("type" to JsonPrimitive("pr_monitor_wake")))
        compose.setContent {
            IntentTheme {
                ConversationComposer(
                    ConversationSendState(
                        queue = listOf(
                            QueuedMessage(
                                "p1",
                                "Monitoring PR intent-hq/android: #5",
                                "",
                                0,
                                metadata
                            ),
                            QueuedMessage(
                                "p2",
                                "Monitoring PR intent-hq/android: #6",
                                "",
                                1,
                                metadata
                            )
                        )
                    ),
                    { true },
                    {},
                    { _, _ -> false }
                )
            }
        }
        compose.onNodeWithText("▸ 2 queued background updates").assertIsDisplayed()
        screenshot("queue-compact")
        compose.onNodeWithText("Monitoring PR", substring = true).assertDoesNotExist()
        compose.onNodeWithText("▸ 2 queued background updates").performClick()
        compose.onNodeWithText("Queued: Monitoring PR intent-hq/android: #5").assertIsDisplayed()
        compose.onNodeWithText("Queued: Monitoring PR intent-hq/android: #6").assertIsDisplayed()
        screenshot("queue-expanded")
        compose.onNodeWithText("▾ 2 queued background updates").performClick()
        compose.onNodeWithText("Monitoring PR", substring = true).assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun show(state: ConversationState, older: () -> Unit = {}, refresh: () -> Unit = {}) {
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(state, older, { _, _ -> }, refresh)
                }
            }
        }
    }

    private fun messages() = (0 until 50).map(::message)

    private fun message(index: Int) = ConversationMessage(
        "message-$index",
        "user",
        listOf(ContentBlock("text", text = "Message $index")),
        "12:00"
    )
}
