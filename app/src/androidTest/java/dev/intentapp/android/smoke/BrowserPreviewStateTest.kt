package dev.intentapp.android.smoke

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import dev.intentapp.android.browser.BrowserScreen
import dev.intentapp.android.browser.BrowserState
import dev.intentapp.android.testing.IntentTestTheme
import dev.intentapp.android.testing.createIntentComposeRule
import org.junit.Rule
import org.junit.Test

class BrowserPreviewStateTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun previewContentSurvivesActivityRecreation() {
        val state = mutableStateOf(BrowserState(url = "http://localhost:3000"))
        compose.setContent {
            IntentTestTheme { BrowserScreen(state.value, {}, {}) { Text("Preview page") } }
        }
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.recreateActivity()
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Cannot load preview") }
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Cannot load preview").assertIsDisplayed()
    }
}
