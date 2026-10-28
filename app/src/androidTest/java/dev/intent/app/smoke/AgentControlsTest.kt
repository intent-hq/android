package dev.intent.app.smoke

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.agents.AgentListScreen
import dev.intent.app.agents.AgentListState
import dev.intent.app.connection.ConnectionController
import dev.intent.app.connection.ConnectionRoot
import dev.intent.app.connection.createConnectionSession
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.JsonRpcMessage
import dev.intent.core.protocol.models.Agent
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.core.protocol.session.SessionTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentControlsTest {
    @get:Rule val compose = createIntentComposeRule()
    private lateinit var server: SmokeServer

    @Test fun longAgentNameAndPlainPreview() {
        val name = "Review the complete Android companion agent list " +
            "and preserve every long task name"
        compose.setContent {
            IntentTheme(true) {
                Surface(Modifier.fillMaxSize()) {
                    AgentListScreen(
                        AgentListState(
                            listOf(
                                Agent(
                                    "long",
                                    name,
                                    digest = "Read [the Spec](intent://local/note/spec) " +
                                        "and **keep formatting plain**."
                                )
                            ),
                            isLoading = false
                        ),
                        onRefresh = {},
                        onAgentSelected = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Read the Spec and keep formatting plain.").assertIsDisplayed()
        screenshot("agent-plain-preview-dark")
        compose.onNodeWithText(name).performTouchInput { longClick() }
        compose.onNodeWithText("Read full name").performClick()
        compose.onNode(hasText(name) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        screenshot("agent-full-name-dark")
        compose.onNodeWithText("Close").performClick()
    }

    @Test fun renameAndDeleteConfirmOrCancel() = connected {
        openConversation()
        menu("Rename agent")
        compose.onNodeWithText("Agent name").performTextReplacement("Renamed agent")
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(server.requests.any { it.method == "agent.rename" })
        menu("Rename agent")
        compose.onNodeWithText("Agent name").performTextReplacement("Renamed agent")
        compose.onNodeWithText("Save").performClick()
        waitFor("Renamed agent")
        assertEquals("Renamed agent", request("agent.rename")["name"]?.jsonPrimitive?.content)
        menu("Delete agent")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Message").assertIsDisplayed()
        assertFalse(server.requests.any { it.method == "agent.delete" })
        menu("Delete agent")
        compose.onNodeWithText("Delete").performClick()
        waitFor("No agents yet")
        compose.onNodeWithContentDescription("Message").assertDoesNotExist()
        request("agent.delete")
    }

    @Test fun longPressMutesAndUnmutesAgent() = connected {
        compose.onNodeWithText("Test agent").performTouchInput { longClick() }
        compose.onNodeWithText("Mute notifications").performClick()
        waitFor("Idle · Notifications muted")
        assertEquals(
            "true",
            request(
                "agent.update"
            )["changes"]?.jsonObject?.get("notificationsMuted")?.jsonPrimitive?.content
        )
        compose.onNodeWithText("Test agent").performTouchInput { longClick() }
        compose.onNodeWithText("Unmute notifications").performClick()
        waitFor("Idle")
        assertEquals(
            "false",
            request(
                "agent.update"
            )["changes"]?.jsonObject?.get("notificationsMuted")?.jsonPrimitive?.content
        )
    }

    @Test fun changesProviderModelAndReasoning() = connected {
        openConversation()
        menu("Model settings")
        waitFor("Other provider")
        compose.onNodeWithText("Other provider").performClick()
        compose.onNodeWithText("Show models").performClick()
        waitFor("New model")
        compose.onNodeWithText("New model").performScrollTo().performClick()
        compose.onNodeWithText("Reasoning").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        compose.onNodeWithText("high").performScrollTo().performClick()
        screenshot("agent-model-settings-light")
        compose.onNodeWithText("Save").performClick()
        waitFor("Message")
        compose.onNodeWithText("Provider default").assertDoesNotExist()
        val model = request("agent.setModel")
        assertEquals("other", model["providerId"]?.jsonPrimitive?.content)
        assertEquals("new", model["modelId"]?.jsonPrimitive?.content)
        assertEquals(
            "high",
            request(
                "agent.update"
            )["changes"]?.jsonObject?.get("reasoningEffort")?.jsonPrimitive?.content
        )
        menu("Model settings")
        waitFor("New model")
        compose.onNodeWithText("Show models").performClick()
        compose.onNodeWithText("Provider default").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        waitFor("Message")
        assertEquals("default", request("agent.setModel")["modelId"]?.jsonPrimitive?.content)
        assertEquals(
            "null",
            request("agent.update")["changes"]?.jsonObject?.get("reasoningEffort").toString()
        )
    }

    @Test fun largeModelCatalogKeepsActionsVisible() = connected(
        mapOf(
            "models.list" to """{"models":[${(1..24).joinToString(",") {
                """{"id":"m$it","name":"Model choice $it","provider":"other"}"""
            }}]}"""
        )
    ) {
        openConversation()
        menu("Model settings")
        waitFor("Other provider")
        compose.onNodeWithText("Other provider").performClick()
        compose.onNodeWithText("Reasoning").assertIsDisplayed()
        compose.onNodeWithText("Show models").performClick()
        waitFor("Model choice 24")
        compose.onNodeWithText("Scroll for more settings ↓").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
        screenshot("agent-model-overflow-light")
        compose.onNodeWithText("Model choice 24").performScrollTo().performClick()
        compose.onNodeWithText("Reasoning").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(server.requests.any { it.method == "agent.setModel" })
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        directory.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun openConversation() {
        compose.onNodeWithText("Test agent").performClick()
        waitFor("Message")
    }

    private fun menu(label: String) {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun request(method: String): JsonObject {
        val params = server.requests.last { it.method == method }.params!!.jsonObject
        assertEquals("workspace", params["workspaceId"]?.jsonPrimitive?.content)
        assertEquals("agent", params["agentId"]?.jsonPrimitive?.content)
        return params
    }

    private fun connected(extra: Map<String, String> = emptyMap(), test: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
            "workspace-sections",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
        val repository = MemoryConnections()
        val saved = repository.add(
            PairingPayload(listOf("desktop.test"), 443, "smoke-token", "AB".repeat(32))
        )
        repository.select(saved.id)
        ConnectionController(repository, { connection, callback ->
            createConnectionSession(connection, callback, transport = {
                val replies = (responses + extra).toMutableMap()
                val fixture = SmokeServer(it, replies).also { server = it }
                object : SessionTransport by fixture {
                    override fun send(message: JsonRpcMessage): Boolean {
                        if (message is JsonRpcMessage.Request) updateFixture(message, replies)
                        return fixture.send(message)
                    }
                }
            })
        }).use { controller ->
            controller.restore()
            compose.setContent { IntentTheme(false) { ConnectionRoot(controller, {}) } }
            waitFor("Android smoke workspace")
            compose.onNodeWithText("Android smoke workspace").performClick()
            waitFor("Test agent")
            test()
        }
    }

    private fun updateFixture(
        request: JsonRpcMessage.Request,
        replies: MutableMap<String, String>
    ) {
        val params = request.params?.jsonObject ?: return
        val agent = Json.parseToJsonElement(
            replies.getValue("agent.get")
        ).jsonObject["agent"]!!.jsonObject
        val updated = buildJsonObject {
            agent.forEach { (key, value) -> put(key, value) }
            when (request.method) {
                "agent.rename" -> put("name", params.getValue("name"))
                "agent.setModel" -> {
                    put("provider", params.getValue("providerId"))
                    put("model", params.getValue("modelId"))
                }
                "agent.update" -> params.getValue("changes").jsonObject.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
        replies["agent.get"] = """{"agent":$updated}"""
        replies["agent.list"] = if (request.method == "agent.delete") {
            """{"agents":[]}"""
        } else if (request.method.startsWith("agent.") &&
            request.method in setOf("agent.rename", "agent.setModel", "agent.update")
        ) {
            """{"agents":[$updated]}"""
        } else {
            replies.getValue("agent.list")
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val AGENT =
            """{"id":"agent","name":"Test agent","provider":"current","model":"old"}"""
        val responses = mapOf(
            "agent.list" to """{"agents":[$AGENT]}""",
            "agent.get" to """{"agent":$AGENT}""",
            "agent.getConversation" to """{"agentId":"agent","messages":[]}""",
            "agent.getQueue" to """{"success":true,"queue":[]}""",
            "chat.subscribe" to """{"subscriptionId":"chat"}""",
            "chat.snapshot" to
                """{"subscriptionId":"chat","kind":"snapshot","seq":0,"snapshot":{"agentId":"agent","messages":[]}}""",
            "chat.unsubscribe" to "{}",
            "agent.rename" to "{}",
            "agent.delete" to "{}",
            "agent.setModel" to "{}",
            "agent.update" to "{}",
            "providers.catalog" to
                """{"providers":[{"id":"other","name":"Other provider","visible":true,"canBeDisabled":false}]}""",
            "settings.get" to """{"path":"providers.enabled","value":null}""",
            "host.providerAuthStatus" to """{"providers":[{"id":"other","authenticated":true}]}""",
            "models.list" to
                """{"models":[{"id":"new","name":"New model","provider":"other","effortLevels":["low","high"]}]}"""
        )
    }
}
