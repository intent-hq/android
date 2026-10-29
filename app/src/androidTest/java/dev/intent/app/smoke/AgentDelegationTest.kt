package dev.intent.app.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.conversation.WorkspaceConversations
import dev.intent.app.design.IntentTheme
import dev.intent.app.history.LocalHistoryAgent
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SessionState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentDelegationTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun opensCoordinatorWithLiveDelegationPayloadDespiteMissingSummaryFlags() {
        val payload = InstrumentationRegistry.getInstrumentation().context.assets
            .open("agent-watches-live.json").bufferedReader().use { it.readText() }
        val parent = """{"id":"parent","name":"Coordinator",
            "workspaceId":"port-create","status":"idle"}"""
        val ready = AtomicBoolean()
        val responses = mapOf(
            "agent.getSubscriptions" to payload,
            "agent.list" to """{"agents":[$parent]}""",
            "agent.get" to """{"agent":$parent}""",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "agent.getConversation" to """{"agentId":"parent","messages":[]}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to
                """{"subscriptionId":"chat","type":"snapshot","data":{"messages":[],"hasMore":false}}"""
        )
        RpcSession(
            { SmokeServer(it, responses) },
            JsonObject(emptyMap()),
            onState = { ready.set(it == SessionState.READY) }
        ).use { session ->
            session.connect()
            compose.waitUntil(5_000) { ready.get() }
            compose.setContent {
                IntentTheme {
                    Surface(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalHistoryAgent provides "parent") {
                            WorkspaceConversations(session, "port-create", {})
                        }
                    }
                }
            }
            // Compose can be idle while the session executor still loads the watch payload.
            compose.waitUntil(5_000) {
                compose.onNodeWithText("Waiting for all (0/3)").isDisplayed() &&
                    compose.onNodeWithText("Plain stop button · Running").isDisplayed() &&
                    compose.onNodeWithText("Create and delete terminals · Running").isDisplayed()
            }
            compose.onNodeWithText("Waiting for all (0/3)").assertIsDisplayed()
            compose.onNodeWithText("Plain stop button · Running").assertIsDisplayed()
            compose.onNodeWithText("Create and delete terminals · Running").assertIsDisplayed()
        }
    }
}
