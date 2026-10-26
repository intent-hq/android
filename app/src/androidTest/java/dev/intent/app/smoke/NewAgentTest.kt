package dev.intent.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.connection.ConnectionController
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.pairing.PairingPayload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewAgentTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun createFromWorkspaceOpensConversation() {
        val repository = MemoryConnections()
        val saved = repository.add(
            PairingPayload(listOf("desktop.test"), 443, "smoke-token", "AB".repeat(32))
        )
        repository.select(saved.id)
        lateinit var server: SmokeServer
        ConnectionController(repository, { connection, callback ->
            createConnectionSession(connection, callback, transport = {
                SmokeServer(it, responses).also { fixture -> server = fixture }
            })
        }).use { controller ->
            controller.restore()
            compose.setContent { IntentTheme { ConnectionRoot(controller, {}) } }
            waitFor("Android smoke workspace")
            compose.onNodeWithText("Android smoke workspace").performClick()
            compose.onNodeWithText("Agents").performClick()
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.onNodeWithText("New agent").performClick()
            waitFor("Worker")
            compose.onNodeWithText("Next").performClick()
            waitFor("Test model")
            compose.onNodeWithText("Test model").performScrollTo().performClick()
            compose.onNodeWithText("high").performScrollTo().performClick()
            compose.onNodeWithText("Create agent").performClick()
            waitFor("Message")
            compose.onNodeWithText("Message").assertIsDisplayed()
            val request = server.requests.single {
                it.method == "agent.create"
            }.params as JsonObject
            assertEquals(JsonPrimitive("workspace"), request["workspaceId"])
            assertEquals(JsonPrimitive("worker"), request["specialistId"])
            assertEquals(JsonPrimitive("codex"), request["provider"])
            assertEquals(JsonPrimitive("test-model"), request["model"])
            assertEquals(JsonPrimitive("high"), request["reasoningEffort"])
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(
                hasText(text)
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private companion object {
        val responses = mapOf(
            "specialist.list" to """{"specialists":[{"id":"worker","name":"Worker"}]}""",
            "providers.catalog" to
                """{"providers":[{"id":"codex","name":"Codex","visible":true,"canBeDisabled":false}]}""",
            "settings.get" to """{"path":"providers.enabled","value":null}""",
            "host.providerAuthStatus" to """{"providers":[{"id":"codex","authenticated":true}]}""",
            "models.list" to
                """{"models":[{"id":"test-model","name":"Test model","provider":"codex","effortLevels":["low","high"]}]}""",
            "agent.create" to """{"agent":{"id":"new-agent","name":"Worker"}}""",
            "agent.get" to """{"agent":{"id":"new-agent","name":"Worker"}}""",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "agent.getConversation" to """{"agentId":"new-agent","messages":[]}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to
                """{"subscriptionId":"chat","kind":"snapshot","seq":0,"snapshot":{"agentId":"new-agent","messages":[]}}""",
            "chat.unsubscribe" to "{}"
        )
    }
}
