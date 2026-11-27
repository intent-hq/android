package dev.intentapp.android.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intentapp.android.connection.ConnectionController
import dev.intentapp.android.connection.ConnectionRoot
import dev.intentapp.android.connection.createConnectionSession
import dev.intentapp.android.design.IntentTheme
import dev.intentapp.android.testing.createIntentComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtilityBackNavigationTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun pairingReturnsThroughConnectionsAndSettingsToWorkspaces() {
        val repository = MemoryConnections()
        val saved = repository.add(
            PairingPayload(listOf("desktop.test"), 443, "smoke-token", "AB".repeat(32))
        )
        repository.select(saved.id)
        ConnectionController(repository, { connection, callback ->
            createConnectionSession(connection, callback, transport = { SmokeServer(it) })
        }).use { controller ->
            controller.restore()
            compose.setContent { IntentTheme { ConnectionRoot(controller, {}) } }
            compose.waitUntil(TIMEOUT) { controller.state.value.session != null }
            compose.waitUntil(TIMEOUT) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Android smoke workspace"))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            compose.onNodeWithContentDescription("Open app launcher").performClick()
            compose.onNodeWithTag("launcher.tiles").performScrollToNode(hasText("Settings"))
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Manage connections").performScrollTo().performClick()
            compose.onNodeWithText("Add connection").performClick()
            compose.onNodeWithText("Scan QR Code").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Add connection").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Manage connections").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Workspaces").assertIsDisplayed()
            compose.onNodeWithText("Android smoke workspace").assertIsDisplayed()
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
