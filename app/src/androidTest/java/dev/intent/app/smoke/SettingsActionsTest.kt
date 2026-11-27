package dev.intent.app.smoke

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.connection.AcknowledgementsPage
import dev.intent.app.connection.ConnectionDiagnostics
import dev.intent.app.connection.ConnectionSettings
import dev.intent.app.connection.ConnectionState
import dev.intent.app.connection.SavedConnection
import dev.intent.app.connection.bundledAcknowledgements
import dev.intent.app.design.IntentTheme
import dev.intent.app.settings.AppPreferences
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.pairing.PairingPayload
import dev.intent.tailcat.Tailcat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActionsTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun pairingManagementAndDisconnectCallDistinctActions() {
        var paired = 0
        var managed = 0
        var disconnected = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = ConnectionState(
            connections = listOf(
                SavedConnection("one", PairingPayload(listOf("desktop.local"), 443, "secret"))
            ),
            activeId = "one"
        )
        compose.setContent {
            IntentTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    ConnectionSettings(
                        diagnostics = ConnectionDiagnostics(),
                        preferences = AppPreferences(context),
                        state = state,
                        onDisconnect = { disconnected++ },
                        onPair = { paired++ },
                        onConnections = { managed++ }
                    )
                }
            }
        }
        compose.onNodeWithText("Pair computer").performScrollTo()
        screenshot("settings-pairing-light")
        compose.onNodeWithText("Pair computer").performClick()
        compose.runOnIdle {
            assertEquals(1, paired)
            assertEquals(0, managed)
            assertEquals(0, disconnected)
        }
        compose.onNodeWithText("Manage connections").performScrollTo().performClick()
        compose.onNodeWithText("Disconnect").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, managed)
            assertEquals(1, disconnected)
            assertEquals(1, state.connections.size)
        }
    }

    @Test
    fun acknowledgementsRowOpensPage() {
        var opened = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            IntentTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize()) {
                    ConnectionSettings(
                        diagnostics = ConnectionDiagnostics(),
                        preferences = AppPreferences(context),
                        onAcknowledgements = { opened++ },
                        onConnections = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Acknowledgements").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun acknowledgementsPageShowsEveryBundledLicense() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = bundledAcknowledgements(context)
        var dark by mutableStateOf(false)
        compose.setContent {
            IntentTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    AcknowledgementsPage(Modifier.systemBarsPadding())
                }
            }
        }
        for (theme in listOf("light", "dark")) {
            dark = theme == "dark"
            entries.forEachIndexed { index, entry ->
                compose.onNodeWithTag("settings.acknowledgements").performScrollToIndex(index)
                compose.onNodeWithText(entry.name.uppercase()).assertIsDisplayed()
                compose.onNodeWithText(entry.licenseTitle).assertIsDisplayed()
                if (index == 0 || entry.name == "Tailcat") {
                    screenshot("settings-acknowledgements-${entry.name.lowercase()}-$theme")
                }
            }
        }
        assertEquals(Tailcat.available, entries.any { it.name == "Tailcat" })
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) }
            ?: java.io.File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
