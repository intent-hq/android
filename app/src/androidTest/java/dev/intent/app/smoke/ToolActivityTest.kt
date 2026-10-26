package dev.intent.app.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.conversation.ConversationScreen
import dev.intent.app.conversation.ConversationState
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolActivityTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun adjacentMessagesCollapseAndHydrateWithOriginalIds() {
        var hydrated: Pair<String, String>? = null
        val messages = listOf(
            tool("call", "tool_use"),
            tool("result", "tool_result").copy(
                contentBlocks = listOf(
                    ContentBlock(
                        "tool_result",
                        id = "result:block",
                        outputTruncated = true,
                        text = "Partial result"
                    )
                )
            ),
            prose(0)
        )
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(
                        ConversationState(messages = messages, isLoading = false),
                        {},
                        { message, block -> hydrated = message to block },
                        {}
                    )
                }
            }
        }
        compose.onNodeWithText("Answer 0").assertIsDisplayed()
        compose.onNodeWithText("▸ tool_use").assertDoesNotExist()
        compose.onNodeWithText("Partial result").assertDoesNotExist()
        compose.onNodeWithText("▸ Tool activity · 2 items").performClick()
        compose.onNodeWithText("▸ tool result").performClick()
        compose.runOnIdle { assertEquals("result" to "result:block", hydrated) }
        compose.onNodeWithText("Partial result").assertIsDisplayed()
        compose.onNodeWithText("▾ Tool activity · 2 items").performClick()
        compose.onNodeWithText("Partial result").assertDoesNotExist()
        compose.onNodeWithText("Answer 0").assertIsDisplayed()
    }

    @Test
    fun prependingAdjacentActivityKeepsVisibleGroupBounds() {
        val state = mutableStateOf(
            ConversationState(
                messages =
                listOf(tool("call", "tool_use"), tool("result", "tool_result")) +
                    (0..30).map(::prose),
                nextToken = "older",
                isLoading = false
            )
        )
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(state.value, {}, { _, _ -> }, {})
                }
            }
        }
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
        compose.onNodeWithTag("transcript").performScrollToIndex(31)
        val before = compose.onNodeWithText("▸ Tool activity · 2 items")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.value = state.value.copy(
                messages = listOf(tool("older", "thinking")) + state.value.messages,
                nextToken = "more"
            )
        }
        compose.waitForIdle()
        assertEquals(
            before,
            compose.onNodeWithText("▸ Tool activity · 3 items")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        )
    }

    private fun tool(id: String, type: String) =
        ConversationMessage(id, "assistant", listOf(ContentBlock(type, id = "$id:block")))

    private fun prose(index: Int) = ConversationMessage(
        "prose-$index",
        "assistant",
        listOf(ContentBlock("text", text = "Answer $index"))
    )
}
