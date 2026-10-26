package dev.intent.app.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.conversation.ConversationScreen
import dev.intent.app.conversation.ConversationSession
import dev.intent.app.conversation.ConversationState
import dev.intent.app.conversation.ConversationStore
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.ChatPush
import dev.intent.core.protocol.models.ContentBlock
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.models.ConversationPage
import dev.intent.core.protocol.models.MessageBlockResult
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationHistoryTest {
    @get:Rule val compose = createIntentComposeRule()

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
    fun exhaustedHistoryRefreshKeepsTheVisibleMessageAndOffset() {
        val session = HistoryRefreshSession()
        val store = ConversationStore(session, "agent")
        store.start()
        session.snapshot(page(0), null)
        val state = mutableStateOf(store.state.value)
        var refreshes = 0
        show(state, refresh = {
            refreshes++
            store.refresh()
            state.value = store.state.value
        })
        compose.onNodeWithTag("transcript").performScrollToIndex(19)
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
        val anchor = compose.onNodeWithText("Message 0").assertIsDisplayed()
        val before = anchor.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            session.snapshot(page(0).takeLast(5), "older")
            state.value = store.state.value
        }
        compose.waitForIdle()
        assertEquals(before, anchor.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { store.close() }
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

private class HistoryRefreshSession : ConversationSession {
    private lateinit var push: (ChatPush) -> Unit

    override fun subscribe(
        onPush: (ChatPush) -> Unit,
        onSubscribed: () -> Unit,
        onError: (Throwable) -> Unit
    ): Closeable {
        push = onPush
        onSubscribed()
        return Closeable {}
    }

    fun snapshot(messages: List<ConversationMessage>, token: String?) {
        push(
            ChatPush(
                "s",
                "snapshot",
                0,
                snapshot = ConversationPage("agent", messages, nextToken = token)
            )
        )
    }

    override fun history(token: String): CompletableFuture<ConversationPage> = error("Not expected")

    override fun hydrate(
        messageId: String,
        blockId: String
    ): CompletableFuture<MessageBlockResult> = error("Not expected")
}
