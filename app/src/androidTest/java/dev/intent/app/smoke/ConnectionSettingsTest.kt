package dev.intent.app.smoke

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.connection.AppearanceSelector
import dev.intent.app.connection.ConnectionScreen
import dev.intent.app.connection.ConnectionState
import dev.intent.app.connection.SavedConnection
import dev.intent.app.design.IntentTheme
import dev.intent.app.settings.Appearance
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionSettingsTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun detailsShowKnownPathsAndFollowTheActiveRouteWithoutExposingTokens() {
        val saved = SavedConnection(
            "one",
            PairingPayload(
                listOf("192.168.1.20", "100.100.1.2"),
                443,
                "bearer-secret",
                "ab".repeat(32),
                "tc-secret"
            ),
            dev.intent.app.connection.ConnectionMetadata("Studio Mac", 1_700_000_000_000L)
        )
        val state = mutableStateOf(
            ConnectionState(
                connections = listOf(saved),
                activeId = "one",
                status = "Connected",
                activePath = dev.intent.app.connection.directPath("100.100.1.2", 443)
            )
        )
        compose.setContent {
            IntentTheme(darkTheme = true) {
                androidx.compose.material3.Surface { ConnectionScreen(state.value, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("Studio Mac").assertExists()
        compose.onNodeWithText("Direct VPN · 100.100.1.2:443").assertExists()
        screenshot("connection-card")
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Connection details").performClick()
        compose.onNodeWithText("Direct LAN · 192.168.1.20:443").assertExists()
        compose.onNodeWithText("Direct VPN · 100.100.1.2:443 · In use").assertExists()
        compose.onNodeWithText("abababababab…abababababab").assertExists()
        compose.onNodeWithText("Copy fingerprint").assertExists()
        screenshot("connection-details")
        compose.onNodeWithText("bearer-secret", substring = true).assertDoesNotExist()
        compose.onNodeWithText("tc-secret", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            state.value =
                state.value.copy(activePath = dev.intent.app.connection.tunnelPath("tc-secret"))
        }
        compose.onNodeWithText("Path in use: Tunnel").assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(status = "Connecting", activePath = null)
        }
        compose.onNodeWithText("Path in use: Not connected").assertExists()
    }

    @Test
    fun drawerUsesReportedNameAndLivePath() {
        val saved = SavedConnection(
            "one",
            PairingPayload(listOf("192.168.1.20"), 443, "secret"),
            dev.intent.app.connection.ConnectionMetadata("Studio Mac")
        )
        compose.setContent {
            IntentTheme {
                dev.intent.app.drawers.ConnectionsDrawer(
                    ConnectionState(
                        connections = listOf(saved),
                        activeId = "one",
                        status = "Connected",
                        activePath = dev.intent.app.connection.directPath("192.168.1.20", 443)
                    ),
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithText("Studio Mac").assertExists()
        compose.onNodeWithText("Direct LAN · 192.168.1.20:443").assertExists()
    }

    @Test
    fun appearanceSelectionMovesWithTheSelectedValue() {
        val appearance = mutableStateOf(Appearance.System)
        compose.setContent {
            IntentTheme { AppearanceSelector(appearance.value) { appearance.value = it } }
        }
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Dark").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(Appearance.Dark, appearance.value) }
    }

    @Test
    fun forgetRequiresMenuAndConfirmationAndCancelKeepsTheConnection() {
        var forgotten: String? = null
        var selected: String? = null
        val state = ConnectionState(
            connections = listOf(
                SavedConnection("one", PairingPayload(listOf("desktop.local"), 443, "secret"))
            )
        )
        compose.setContent {
            IntentTheme {
                ConnectionScreen(state, { selected = it }, { forgotten = it }, {})
            }
        }
        compose.onNodeWithText("Forget connection").assertDoesNotExist()
        compose.onNodeWithText("Connect").performClick()
        compose.runOnIdle { assertEquals("one", selected) }
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Forget connection").performClick()
        compose.onNodeWithText(
            "desktop.local:443\n\n" +
                "This removes the saved connection from this device. You must pair again to reconnect."
        ).assertExists()
        compose.runOnIdle { assertEquals(null, forgotten) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, forgotten) }
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Forget connection").performClick()
        compose.onNodeWithText("Forget connection").performClick()
        compose.runOnIdle { assertEquals("one", forgotten) }
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
}
