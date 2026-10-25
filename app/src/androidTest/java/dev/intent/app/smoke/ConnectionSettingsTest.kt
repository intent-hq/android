package dev.intent.app.smoke

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.connection.AppearanceSelector
import dev.intent.app.connection.ConnectionScreen
import dev.intent.app.connection.ConnectionState
import dev.intent.app.connection.SavedConnection
import dev.intent.app.design.IntentTheme
import dev.intent.app.settings.Appearance
import dev.intent.core.protocol.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionSettingsTest {
    @get:Rule val compose = createComposeRule()

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
        compose.runOnIdle { assertEquals(null, forgotten) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(null, forgotten) }
        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Forget connection").performClick()
        compose.onNodeWithText("Forget connection").performClick()
        compose.runOnIdle { assertEquals("one", forgotten) }
    }
}
