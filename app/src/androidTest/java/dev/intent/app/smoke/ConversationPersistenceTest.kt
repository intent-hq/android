package dev.intent.app.smoke

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.conversation.ConversationDraftStore
import dev.intent.app.conversation.ConversationRoute
import dev.intent.app.conversation.ConversationSeen
import dev.intent.app.conversation.ConversationSeenEffect
import dev.intent.app.conversation.ConversationSendStore
import dev.intent.app.conversation.ConversationState
import dev.intent.app.conversation.ConversationStore
import dev.intent.app.conversation.RpcConversationCommands
import dev.intent.app.conversation.RpcConversationSession
import dev.intent.app.conversation.markConversationSeen
import dev.intent.app.conversation.scheduleSeen
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.ConversationMessage
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConversationPersistenceTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun draftSurvivesRouteDisposalRecreationAndConfirmedSendClearsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(
            "conversation-drafts",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        val visible = mutableStateOf(true)
        var server: SmokeServer? = null
        val ready = AtomicBoolean()
        RpcSession({ listener ->
            SmokeServer(listener, responses()).also { server = it }
        }, JsonObject(emptyMap()), onState = {
            ready.set(it == SessionState.READY)
        }).use { session ->
            session.connect()
            compose.waitUntil(TIMEOUT) { ready.get() }
            compose.setContent {
                IntentTheme {
                    Surface(Modifier.fillMaxSize()) {
                        if (visible.value) {
                            val app = LocalContext.current.applicationContext
                            val draft =
                                remember { ConversationDraftStore(app, "account").draft("w", "a") }
                            val store =
                                remember {
                                    ConversationStore(
                                        RpcConversationSession(session, "w", "a"),
                                        "a"
                                    )
                                }
                            val sends =
                                remember {
                                    ConversationSendStore(
                                        RpcConversationCommands(session, "w", "a"),
                                        "a"
                                    )
                                }
                            val seen = remember {
                                ConversationSeen({
                                    markConversationSeen(session, "w", "a", it)
                                }, ::scheduleSeen)
                            }
                            DisposableEffect(store, sends) {
                                onDispose {
                                    store.close()
                                    sends.close()
                                }
                            }
                            ConversationRoute(store, sends, draft = draft, seen = seen)
                        }
                    }
                }
            }
            compose.onNodeWithContentDescription(
                "Message"
            ).performTextInput("Keep across navigation")
            assertEquals(0, server!!.requests.count { it.method == "agent.markSeen" })
            compose.runOnIdle { visible.value = false }
            compose.waitUntil(TIMEOUT) { server!!.requests.any { it.method == "agent.markSeen" } }
            val seen = server!!.requests.single {
                it.method == "agent.markSeen"
            }.params!!.jsonObject
            assertEquals("w", seen["workspaceId"]!!.jsonPrimitive.content)
            assertEquals("a", seen["agentId"]!!.jsonPrimitive.content)
            assertEquals("reply", seen["messageId"]!!.jsonPrimitive.content)
            compose.runOnIdle { visible.value = true }
            compose.onNodeWithContentDescription(
                "Message"
            ).assertTextEquals("Keep across navigation")
            compose.recreateActivity()
            compose.onNodeWithContentDescription(
                "Message"
            ).assertTextEquals("Keep across navigation")
            compose.onNodeWithText("Send").performClick()
            compose.waitUntil(TIMEOUT) {
                ConversationDraftStore(context, "account").draft("w", "a").text.isEmpty()
            }
            compose.onNodeWithContentDescription("Message").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
            )
            compose.runOnIdle { visible.value = false }
        }
    }

    @Test fun turnCompletionWaitsOneSecondAndDepartureDoesNotDuplicateSeen() {
        val visible = mutableStateOf(true)
        val state = mutableStateOf(
            ConversationState(
                messages = listOf(ConversationMessage("reply", "assistant", isStreaming = true)),
                isLoading = false,
                isStreaming = true
            )
        )
        val calls = CopyOnWriteArrayList<Pair<String, Long>>()
        val seen = ConversationSeen({ id ->
            calls += id to SystemClock.elapsedRealtime()
            CompletableFuture.completedFuture(id)
        }, ::scheduleSeen)
        compose.setContent {
            if (visible.value) ConversationSeenEffect(seen, state.value, null)
        }
        assertTrue(calls.isEmpty())
        val finishedAt = SystemClock.elapsedRealtime()
        compose.runOnIdle {
            state.value = state.value.copy(
                messages = listOf(ConversationMessage("reply", "assistant")),
                isStreaming = false
            )
        }
        compose.waitUntil(TIMEOUT) { calls.isNotEmpty() }
        assertEquals("reply", calls.single().first)
        assertTrue(calls.single().second - finishedAt >= 1000L)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            state.value =
                state.value.copy(messages = listOf(ConversationMessage("hidden", "assistant")))
        }
        assertEquals(1, calls.size)
    }

    private fun responses() = mapOf(
        "chat.subscribe" to """{"subscriptionId":"chat"}""",
        "chat.unsubscribe" to "{}",
        "chat.snapshot" to
            """{"subscriptionId":"chat","kind":"snapshot","seq":0,"snapshot":{"agentId":"a","messages":[{"id":"reply","role":"assistant","contentBlocks":[{"type":"text","text":"A reply"}]}]}}""",
        "agent.getQueue" to """{"success":true,"queue":[]}""",
        "agent.get" to """{"agent":{"id":"a","name":"Agent"}}""",
        "agent.getConversation" to """{"agentId":"a","messages":[]}""",
        "agent.markSeen" to """{"success":true,"lastSeenMessageId":"reply"}"""
    )
}

private const val TIMEOUT = 10_000L
