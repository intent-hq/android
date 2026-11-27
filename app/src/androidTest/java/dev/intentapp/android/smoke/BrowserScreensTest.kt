package dev.intentapp.android.smoke

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import dev.intentapp.android.browser.BrowserScreen
import dev.intentapp.android.browser.BrowserState
import dev.intentapp.android.browser.MemorySavedBrowserPersistence
import dev.intentapp.android.browser.NewBrowserForm
import dev.intentapp.android.browser.SavedBrowser
import dev.intentapp.android.browser.SavedBrowserPersistence
import dev.intentapp.android.browser.SavedBrowserStore
import dev.intentapp.android.browser.WorkspaceBrowsersScreen
import dev.intentapp.android.testing.IntentTestTheme
import dev.intentapp.android.testing.createIntentComposeRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Saved browsers list, new browser, remove, error/retry and page states from iOS. */
class BrowserScreensTest {
    @get:Rule val compose = createIntentComposeRule()
    private val dark = mutableStateOf(false)

    @Test
    fun emptyListCreatesABrowserThenOpensAndRemovesIt() {
        val store = SavedBrowserStore(MemorySavedBrowserPersistence()).apply { load() }
        var creating by mutableStateOf(false)
        var opened: SavedBrowser? = null
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) {
                val state by store.state.collectAsState()
                if (creating) {
                    NewBrowserForm({ creating = false }) { address ->
                        store.create(address).map {
                            creating = false
                            opened = it
                        }
                    }
                } else {
                    WorkspaceBrowsersScreen(
                        state,
                        {},
                        { creating = true },
                        { opened = it },
                        store::remove,
                        {}
                    )
                }
            }
        }
        compose.onNodeWithText("No browsers").assertIsDisplayed()
        compose.onNodeWithText("Under development").assertDoesNotExist()
        screenshots("browsers-empty")

        compose.onNodeWithContentDescription("New browser").performClick()
        compose.onNodeWithContentDescription("Browser address").assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsNotEnabled()
        screenshots("browser-new")
        compose.onNodeWithContentDescription("Browser address").performTextInput("user:pw@host")
        compose.onNodeWithText("Open").assertIsEnabled().performClick()
        compose.onNodeWithText(
            "Enter an http or https address without a username or password, such as localhost:3000."
        ).assertIsDisplayed()
        screenshots("browser-new-error")
        compose.onNodeWithContentDescription(
            "Browser address"
        ).performTextReplacement("localhost:5173")
        compose.onNodeWithContentDescription("Browser address").performImeAction()
        assertEquals("http://localhost:5173", opened?.url)

        val created = store.state.value.browsers.single()
        opened = null
        compose.onNodeWithText("BROWSERS").assertIsDisplayed()
        compose.onAllNodesWithText(
            "http://localhost:5173",
            useUnmergedTree = true
        )[0].assertIsDisplayed()
        compose.onNodeWithTag("browser.row.${created.id}").performClick()
        assertEquals(created, opened)

        compose.onNodeWithTag("browser.row.${created.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("browser.delete.${created.id}").assertIsDisplayed()
        screenshots("browsers-swipe-delete")
        compose.onNodeWithTag("browser.delete.${created.id}").performClick()
        compose.onNodeWithText("No browsers").assertIsDisplayed()
        assertNull(store.state.value.browsers.firstOrNull())
    }

    @Test
    fun savedRowsShowAddressesAndLongPressDeletes() {
        val rows = listOf(
            SavedBrowser("browser:a", "Vite dev server", "http://localhost:5173"),
            SavedBrowser(
                "browser:b",
                "http://localhost:3000/settings",
                "http://localhost:3000/settings"
            )
        )
        val store = SavedBrowserStore(
            MemorySavedBrowserPersistence().apply { save(rows) }
        ).apply { load() }
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) {
                val state by store.state.collectAsState()
                WorkspaceBrowsersScreen(state, {}, {}, {}, store::remove, {})
            }
        }
        compose.onNodeWithText("Vite dev server").assertIsDisplayed()
        screenshots("browsers-list")
        compose.onNodeWithTag("browser.row.browser:a").performTouchInput { longClick() }
        compose.onNodeWithText("Delete browser").performClick()
        compose.onNodeWithText("Vite dev server").assertDoesNotExist()
        assertEquals(1, store.state.value.browsers.size)
    }

    @Test
    fun loadErrorShowsRetryAndDisconnectedRows() {
        var failing = true
        val persistence = object : SavedBrowserPersistence {
            override fun load(): List<SavedBrowser> {
                check(!failing)
                return listOf(SavedBrowser("browser:a", "Preview", "http://localhost:3000"))
            }
            override fun save(browsers: List<SavedBrowser>) = Unit
        }
        val store = SavedBrowserStore(persistence).apply { load() }
        var connected by mutableStateOf(true)
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) {
                val state by store.state.collectAsState()
                WorkspaceBrowsersScreen(state, {}, {}, {}, {}, store::load, connected = connected)
            }
        }
        compose.onNodeWithText(
            "Cannot load saved browsers. Retry to recover them."
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("New browser").assertIsNotEnabled()
        screenshots("browsers-error")
        failing = false
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Preview").assertIsDisplayed()
        compose.runOnIdle { connected = false }
        compose.onNodeWithText("Disconnected", useUnmergedTree = true).assertIsDisplayed()
        screenshots("browsers-disconnected")
    }

    @Test
    fun browserPageShowsTitleUnavailableStateAndBottomAddressBar() {
        var state by mutableStateOf(
            BrowserState(
                url = "http://127.0.0.1:41000",
                requestedUrl = "http://localhost:3000",
                title = "Vite App"
            )
        )
        var connected by mutableStateOf(true)
        var retried = 0
        var opened = ""
        compose.setContent {
            IntentTestTheme(darkTheme = dark.value) {
                BrowserScreen(
                    state,
                    {},
                    { opened = it },
                    initialAddress = "http://localhost:3000",
                    onReload = { retried++ },
                    connected = connected
                ) { Text("Preview page") }
            }
        }
        compose.onNodeWithText("Vite App").assertIsDisplayed()
        compose.onNodeWithText("Preview page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close browser").assertIsDisplayed()
        screenshots("browser-page")

        compose.runOnIdle { state = state.copy(url = null, error = "Cannot load this preview.") }
        compose.onNodeWithText("Page unavailable").assertIsDisplayed()
        compose.onNodeWithText("Preview page").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retried)
        screenshots("browser-page-unavailable")

        compose.runOnIdle { connected = false }
        compose.onNodeWithText(
            "Reconnect to your machine to open this browser."
        ).assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reload").assertIsNotEnabled()
        screenshots("browser-page-disconnected")

        compose.runOnIdle {
            connected = true
            state = BrowserState(loading = true, requestedUrl = "http://localhost:3000")
        }
        compose.onNodeWithContentDescription("Loading page").assertIsDisplayed()
        compose.onNodeWithContentDescription("Browser URL").performTextReplacement("bad host")
        compose.onNodeWithContentDescription("Browser URL").performImeAction()
        compose.onNodeWithText(
            "Enter an http or https address without a username or password, such as localhost:3000."
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Browser URL"
        ).performTextReplacement("LOCALHOST:5173/a")
        compose.onNodeWithContentDescription("Browser URL").performImeAction()
        assertEquals("http://localhost:5173/a", opened)
    }

    private fun screenshots(name: String) {
        compose.runOnIdle { dark.value = false }
        screenshot("$name-light")
        compose.runOnIdle { dark.value = true }
        screenshot("$name-dark")
        compose.runOnIdle { dark.value = false }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        check(directory.mkdirs() || directory.isDirectory)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
