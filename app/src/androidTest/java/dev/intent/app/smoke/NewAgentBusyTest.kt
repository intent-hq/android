package dev.intent.app.smoke

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.agents.NewAgentOptionsStore
import dev.intent.app.agents.NewAgentScreen
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.app.workspaces.WorkspaceOperations
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewAgentBusyTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun busyBlocksBackAndFailureKeepsDraft() {
        val host = BusyCreationHost()
        var backCalls = 0
        val store = NewAgentOptionsStore(host)
        compose.setContent {
            IntentTheme { NewAgentScreen(store, "workspace", { backCalls++ }, {}) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Worker")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Next").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(
                hasText("The host default model will be used.")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Reasoning").assertDoesNotExist()
        compose.onNodeWithText("Create agent").performClick()
        try {
            compose.onNodeWithText("Creating…").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Back").assertDoesNotExist()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("Provider & Model").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, backCalls) }
            assertEquals(1, host.creates)
        } finally {
            host.release.countDown()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(
                hasText("Creation was not confirmed. Check the agent list before retrying.")
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Agent name").assertIsDisplayed()
        assertEquals(1, host.creates)
    }
}

private class BusyCreationHost : WorkspaceOperations {
    val release = CountDownLatch(1)

    @Volatile var creates = 0
    override fun request(method: String, params: JsonObject): JsonObject = Json.parseToJsonElement(
        when (method) {
            "specialist.list" -> """{"specialists":[{"id":"worker","name":"Worker"}]}"""
            "providers.catalog" ->
                """{"providers":[{"id":"codex","name":"Codex","visible":true,"canBeDisabled":false}]}"""
            "settings.get" -> """{"path":"providers.enabled","value":null}"""
            "host.providerAuthStatus" -> """{"providers":[{"id":"codex","authenticated":true}]}"""
            "models.list" -> """{"models":[]}"""
            "agent.create" -> {
                creates++
                check(release.await(20, TimeUnit.SECONDS))
                error("Creation rejected")
            }
            else -> error(method)
        }
    ) as JsonObject
    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable =
        Closeable {}
}
