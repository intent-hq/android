package dev.intent.app.smoke

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import dev.intent.app.design.IntentTheme
import dev.intent.app.git.GitCommitScreen
import dev.intent.app.git.GitDiffScreen
import dev.intent.app.git.GitState
import dev.intent.app.testing.createIntentComposeRule
import dev.intent.core.protocol.models.GitCommit
import dev.intent.core.protocol.models.GitCommitFile
import dev.intent.core.protocol.models.GitDiff
import dev.intent.core.protocol.models.GitDiffLine
import dev.intent.core.protocol.models.GitFileStatus
import dev.intent.core.protocol.models.GitHunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiffReadabilityTest {
    @get:Rule val compose = createIntentComposeRule()

    @Test
    fun workingDiffScrollsTogetherWithFixedNumbers() = checkScroll(commit = false)

    @Test
    fun commitDiffScrollsTogetherWithFixedNumbers() = checkScroll(commit = true)

    private fun checkScroll(commit: Boolean) {
        val state = GitState(
            loading = false,
            selected = GitFileStatus("src/example.kt", "M", false),
            selectedCommit = GitCommit("abcdef", message = "Update the example"),
            commitFiles = listOf(GitCommitFile("src/example.kt", 1, 1)),
            diffs = listOf(
                GitDiff(
                    "src/example.kt",
                    listOf(
                        GitHunk(
                            100,
                            1,
                            100,
                            1,
                            listOf(
                                GitDiffLine(
                                    "Deletion",
                                    "old value " + "0123456789 ".repeat(12),
                                    100
                                ),
                                GitDiffLine(
                                    "Addition",
                                    "new value " + "0123456789 ".repeat(15),
                                    newNumber = 100
                                )
                            )
                        )
                    )
                )
            )
        )
        compose.setContent {
            IntentTheme(darkTheme = true) {
                Surface {
                    if (commit) GitCommitScreen(state, {}, {}) else GitDiffScreen(state) {}
                }
            }
        }
        compose.onNodeWithText("↔ Swipe code to see more").assertIsDisplayed()
        val old = compose.onNodeWithTag("diff.line.Deletion.100")
        val new = compose.onNodeWithTag("diff.line.Addition.100")
        val gutter = compose.onNodeWithTag("diff.gutter")
        val oldBefore = old.getUnclippedBoundsInRoot()
        val newBefore = new.getUnclippedBoundsInRoot()
        val gutterBefore = gutter.getUnclippedBoundsInRoot()
        screenshot(if (commit) "commit-diff-before-scroll" else "working-diff-before-scroll")
        compose.onNodeWithTag("diff.code").performTouchInput { swipeLeft() }
        val oldMovement = oldBefore.left - old.getUnclippedBoundsInRoot().left
        val newMovement = newBefore.left - new.getUnclippedBoundsInRoot().left
        assertTrue("Old line must move", oldMovement.value > 0)
        assertEquals(oldMovement.value, newMovement.value, 0.5f)
        assertEquals(gutterBefore, gutter.getUnclippedBoundsInRoot())
        gutter.assertIsDisplayed()
        screenshot(if (commit) "commit-diff-after-scroll" else "working-diff-after-scroll")
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
