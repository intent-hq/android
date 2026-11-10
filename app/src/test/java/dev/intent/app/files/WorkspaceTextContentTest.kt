package dev.intent.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTextContentTest {
    @Test
    fun previewPreservesTextAndLogicalLinesIncludingBlankAndTrailingLines() {
        val text = "first\r\n\nlast\n"
        val preview = boundedWorkspaceText(text)
        assertEquals(text, preview.text)
        assertEquals(listOf(0, 7, 8, 13), preview.lineStarts)
        assertFalse(preview.truncated)
        assertEquals(listOf(0), boundedWorkspaceText("").lineStarts)
    }

    @Test
    fun characterLimitDoesNotSplitSurrogatePairs() {
        val text = "a".repeat(TEXT_LAYOUT_CHAR_LIMIT - 1) + "\uD83D\uDE80tail"
        val preview = boundedWorkspaceText(text)
        assertEquals(TEXT_LAYOUT_CHAR_LIMIT - 1, preview.text.length)
        assertTrue(preview.truncated)
        assertEquals(text.take(TEXT_LAYOUT_CHAR_LIMIT - 1), preview.text)
        assertFalse(boundedWorkspaceText("a".repeat(TEXT_LAYOUT_CHAR_LIMIT)).truncated)
    }

    @Test
    fun manyShortLinesStayBoundedWithoutAddingTextToClipboardContent() {
        val preview = boundedWorkspaceText("x\n".repeat(TEXT_LAYOUT_LINE_LIMIT + 1))
        assertEquals(TEXT_LAYOUT_LINE_LIMIT, preview.lineStarts.size)
        assertEquals("x\n".repeat(TEXT_LAYOUT_LINE_LIMIT - 1) + "x", preview.text)
        assertTrue(preview.truncated)
    }

    @Test
    fun highlightingReusesLexerWithoutChangingSourceAndSkipsLargeFiles() {
        val code = "val name = \"Hello\" // comment\n"
        val tokens = workspaceCodeTokens(code, "src/Example.KT")
        assertEquals(code, tokens.joinToString("") { it.text })
        assertTrue(tokens.any { it.kind == "string" })
        assertTrue(tokens.any { it.kind == "comment" })
        val large = code.repeat(TEXT_HIGHLIGHT_CHAR_LIMIT / code.length + 1)
        assertEquals(listOf("plain"), workspaceCodeTokens(large, "src/Example.kt").map { it.kind })
        assertEquals(listOf("plain"), workspaceCodeTokens(code, "notes.txt").map { it.kind })
    }
}
