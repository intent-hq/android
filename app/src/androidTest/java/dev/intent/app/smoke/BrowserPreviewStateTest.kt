package dev.intent.app.smoke

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.browser.BrowserScreen
import dev.intent.app.browser.BrowserState
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPreviewStateTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun previewContentSurvivesActivityRecreation() {
        val state = mutableStateOf(BrowserState(url = "http://localhost:3000"))
        compose.setContent {
            IntentTheme { BrowserScreen(state.value, {}, {}) { Text("Preview page") } }
        }
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.recreateActivity()
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Cannot load preview") }
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Cannot load preview").assertIsDisplayed()
    }

    @Test
    fun failedPageIsRemovedAndRetryIsBesideTheAddress() {
        val state = mutableStateOf(BrowserState(url = "http://localhost:3000"))
        var opened = ""
        compose.setContent {
            IntentTheme {
                BrowserScreen(state.value, {}, { opened = it }) { Text("Preview page") }
            }
        }
        compose.onNodeWithText("Open").assertIsDisplayed().performClick()
        assertEquals("http://daemon.localhost:3000/", opened)
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Cannot load preview") }
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Cannot load preview").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = BrowserState(loading = true) }
        compose.onNodeWithText("Cannot load preview").assertDoesNotExist()
        compose.onNodeWithText("Open").assertIsDisplayed()
    }
}
