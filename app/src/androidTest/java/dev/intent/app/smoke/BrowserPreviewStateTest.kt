package dev.intent.app.smoke

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import dev.intent.app.browser.BrowserScreen
import dev.intent.app.browser.BrowserState
import dev.intent.app.design.IntentTheme
import dev.intent.app.testing.createIntentComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
        val state =
            mutableStateOf(
                BrowserState(url = "http://localhost:3000", requestedUrl = "http://localhost:3000")
            )
        var retried = false
        compose.setContent {
            IntentTheme {
                BrowserScreen(state.value, {
                }, {}, onReload = { retried = true }) { Text("Preview page") }
            }
        }
        compose.onNodeWithText("Open a workspace preview").assertDoesNotExist()
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Cannot load preview") }
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Cannot load preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Retry preview").assertIsDisplayed().performClick()
        assertEquals(true, retried)
        compose.runOnIdle { state.value = BrowserState(loading = true) }
        compose.onNodeWithText("Cannot load preview").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loading preview").assertIsDisplayed()
    }

    @Test
    fun emptyPreviewShowsGuidanceAndAddressEntryOpensTheServer() {
        var opened = ""
        compose.setContent {
            IntentTheme { BrowserScreen(BrowserState(), {}, { opened = it }) {} }
        }
        compose.onNodeWithText("Open a workspace preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Page forward").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Browser URL").performTextInput("localhost:5173/path")
        compose.onNodeWithContentDescription("Browser URL").performImeAction()
        assertEquals("http://localhost:5173/path", opened)
    }
}
