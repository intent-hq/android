package dev.intent.app.smoke

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.design.IntentTheme
import dev.intent.app.workspaces.CreationOptionsStore
import dev.intent.app.workspaces.WorkspaceCreateScreen
import dev.intent.app.workspaces.WorkspaceOperations
import java.io.Closeable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceCreateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun createStaysVisibleDuringInputAndChoiceSelection() {
        val store = CreationOptionsStore(CreateChoices())
        var keyboard: SoftwareKeyboardController? = null
        compose.setContent {
            IntentTheme {
                keyboard = LocalSoftwareKeyboardController.current
                WorkspaceCreateScreen(store, {}, {})
            }
        }
        compose.onNodeWithText("Create workspace").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Title").performClick().performTextInput("New workspace")
        compose.waitForIdle()
        compose.onNodeWithText("Create workspace").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { keyboard?.hide() }
        compose.onNodeWithText("Create workspace").assertIsDisplayed()
        compose.waitUntil {
            compose.onAllNodes(
                androidx.compose.ui.test.hasText("Provider A")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Provider A").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Create workspace").assertIsDisplayed()
        compose.onNodeWithText("Specialist B").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Create workspace").assertIsDisplayed()
    }
}

private class CreateChoices : WorkspaceOperations {
    override fun request(method: String, params: JsonObject): JsonObject = Json.parseToJsonElement(
        when (method) {
            "providers.catalog" ->
                """{"providers":[{"id":"a","name":"Provider A","visible":true,"canBeDisabled":false}]}"""
            "settings.get" -> """{"path":"providers.enabled","value":null}"""
            "host.providerAuthStatus" -> """{"providers":[{"id":"a","authenticated":true}]}"""
            "specialist.list" -> """{"specialists":[{"id":"b","name":"Specialist B"}]}"""
            "models.list" -> """{"models":[]}"""
            else -> error("Unexpected request: $method")
        }
    ) as JsonObject

    override fun events(workspaceId: String, receive: (JsonObject) -> Unit): Closeable = Closeable {
    }
}
