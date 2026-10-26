package dev.intent.app.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.conversation.ConversationScreen
import dev.intent.app.conversation.ConversationState
import dev.intent.app.design.IntentTheme
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationHistoryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun delayedPagesKeepVisibleMessageAndOffsetWithoutRefreshing() {
        val state = mutableStateOf(
            ConversationState(messages = page(40), nextToken = "40", isLoading = false)
        )
        var refreshes = 0
        var loads = 0
        show(state, older = {
            loads++
            state.value = state.value.copy(loadingOlder = true)
        }, refresh = { refreshes++ })

        for (start in listOf(40, 20)) {
            compose.onNodeWithTag("transcript").performScrollToIndex(state.value.messages.lastIndex)
            compose.runOnIdle { assertEquals(true, state.value.loadingOlder) }
            // The network reply is deliberately held while the user overscrolls the loaded page.
            compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
            compose.runOnIdle { assertEquals(0, refreshes) }
            val anchor = compose.onNodeWithText("Message $start").assertIsDisplayed()
            val before = anchor.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                state.value = state.value.copy(
                    messages = page(start - 20) + state.value.messages,
                    nextToken = if (start == 20) null else "20",
                    loadingOlder = false
                )
            }
            compose.waitForIdle()
            assertEquals(before, anchor.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        }
        compose.runOnIdle { assertEquals(2, loads) }
    }

    @Test
    fun contentGrowthInAnotherRowKeepsReadingPosition() {
        val state = mutableStateOf(ConversationState(messages = page(0), isLoading = false))
        show(state)
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
        compose.onNodeWithTag("transcript").performScrollToIndex(10)
        val anchor = compose.onNodeWithText("Message 9").assertIsDisplayed()
        val before = anchor.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.value = state.value.copy(
                messages = state.value.messages.map {
                    if (it.id == "message-19") {
                        it.copy(
                            contentBlocks = listOf(
                                ContentBlock("text", text = "Updated\n".repeat(30))
                            )
                        )
                    } else {
                        it
                    }
                }
            )
        }
        compose.waitForIdle()
        assertEquals(before, anchor.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun exhaustedHistoryStillAllowsExplicitPullRefresh() {
        val state = mutableStateOf(ConversationState(messages = page(0), isLoading = false))
        var refreshes = 0
        show(state, refresh = { refreshes++ })
        compose.onNodeWithTag("transcript").performScrollToIndex(19)
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    private fun show(
        state: MutableState<ConversationState>,
        older: () -> Unit = {},
        refresh: () -> Unit = {}
    ) {
        compose.setContent {
            IntentTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationScreen(state.value, older, { _, _ -> }, refresh)
                }
            }
        }
    }

    private fun page(start: Int) = (start until start + 20).map {
        ConversationMessage(
            "message-$it",
            "user",
            listOf(ContentBlock("text", text = "Message $it"))
        )
    }
}
