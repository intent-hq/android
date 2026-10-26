package dev.intent.app.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.intent.app.design.IntentTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownTableTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun wrappedCellsShareRowBorders() {
        show(
            """
            | Short | A long header that wraps over several lines |
            | --- | --- |
            | A long description that wraps over several lines in this narrow table cell. | Done |
            | Next | Row |
            """.trimIndent()
        )
        val header = cellBounds(0, 0)
        val body = cellBounds(1, 0)
        val next = cellBounds(2, 0)
        for (row in 0..2) {
            val left = cellBounds(row, 0)
            val right = cellBounds(row, 1)
            assertEquals(left.top, right.top, 0.5f)
            assertEquals(left.bottom, right.bottom, 0.5f)
        }
        assertTrue(header.height > next.height)
        assertTrue(body.height > next.height)
        assertEquals(header.bottom, body.top, 0.5f)
        assertEquals(body.bottom, next.top, 0.5f)
    }

    @Test
    fun wideTableShowsCueAndEveryColumnCanBeReached() {
        show(
            """
            | Task | Result | Owner | Commit |
            | --- | --- | --- | --- |
            | Layout | Done | Sam | abc123 |
            """.trimIndent()
        )
        compose.onNodeWithText("↔ Scroll horizontally for more columns").assertIsDisplayed()
        listOf("Layout", "Done", "Sam", "abc123").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Commit").assertIsDisplayed()
        assertEquals(cellBounds(0, 3).left, cellBounds(1, 3).left, 0.5f)
        compose.onNodeWithText("Layout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Task").assertIsDisplayed()
    }

    @Test
    fun fittingTableDoesNotShowScrollCue() {
        show("| Task |\n| --- |\n| Done |")
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("↔ Scroll horizontally for more columns").assertDoesNotExist()
    }

    private fun cellBounds(row: Int, column: Int) =
        compose.onNodeWithTag("markdown-cell-$row-$column", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

    private fun show(markdown: String) {
        compose.setContent {
            IntentTheme {
                Box(Modifier.width(320.dp)) { MarkdownContent(markdown) }
            }
        }
    }
}
