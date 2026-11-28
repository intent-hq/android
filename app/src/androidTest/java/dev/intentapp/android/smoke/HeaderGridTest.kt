package dev.intentapp.android.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.core.protocol.models.Workspace
import dev.intentapp.android.conversation.ConversationHeader
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentTheme
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.CompactAppBar
import dev.intentapp.android.testing.createIntentComposeRule
import dev.intentapp.android.workspaces.WorkspaceDetailScreen
import dev.intentapp.android.workspaces.WorkspaceListScreen
import dev.intentapp.android.workspaces.WorkspaceListState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every page header puts its More actions button on the same trailing page line. */
@RunWith(AndroidJUnit4::class)
class HeaderGridTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test fun moreActionsShareTheTrailingPageLine() {
        var dark by mutableStateOf(false)
        val actions = listOf(ChromeAction("Rename") {})
        compose.setContent {
            IntentTheme(dark) {
                Box(Modifier.fillMaxSize().background(IntentScreen.canvas)) {
                    Column {
                        Box(Modifier.height(140.dp)) {
                            WorkspaceListScreen(
                                WorkspaceListState(listOf(Workspace("w", "Workspace")), false),
                                {},
                                {}
                            )
                        }
                        HorizontalDivider()
                        WorkspaceDetailScreen(
                            Workspace("w", "Workspace detail"),
                            actions = actions
                        ) {
                        }
                        HorizontalDivider()
                        CompactAppBar("Scripts", onBack = {}, actions = actions)
                        HorizontalDivider()
                        ConversationHeader("Agent", "Agent", {}, actions)
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(end = 24.dp).width(1.dp)
                            .fillMaxHeight().background(Color.Red.copy(alpha = 0.6f))
                    )
                }
            }
        }
        compose.waitForIdle()
        val buttons = compose.onAllNodesWithContentDescription("More actions").fetchSemanticsNodes()
        val rights = buttons.map { it.boundsInRoot.right.toInt() }.toSet()
        screenshot("header-grid-light")
        compose.runOnIdle { dark = true }
        compose.waitForIdle()
        screenshot("header-grid-dark")
        assertEquals("More actions right edges: $rights", 1, rights.size)
    }

    private fun screenshot(name: String) {
        android.os.SystemClock.sleep(1_000)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(instrumentation.targetContext.cacheDir, "screenshots")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
