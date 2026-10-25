package dev.intent.app.terminals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTerminalTest {
    @Test
    fun colorsAndCursorSequencesCanSplitAcrossChunks() {
        val terminal = AnsiTerminal(10, 3)
        terminal.feed("abc\rX\u001b[3".toByteArray())
        terminal.feed("1mR\u001b[0m\u001b[2;3HZ".toByteArray())
        val frame = terminal.frame()
        assertEquals("XRc", line(frame, 0).trimEnd())
        assertEquals(1, frame.lines[0][1].foreground)
        assertEquals('Z', frame.lines[1][2].text)
        assertEquals(7, frame.lines[1][2].foreground)
        assertEquals(1, frame.cursorRow)
        assertEquals(3, frame.cursorColumn)
    }

    @Test
    fun utf8AndOscCanSplitAcrossFrames() {
        val terminal = AnsiTerminal()
        val bytes = "é".toByteArray()
        terminal.feed(bytes.copyOfRange(0, 1))
        terminal.feed(bytes.copyOfRange(1, 2))
        terminal.feed("\u001b]0;secret title\u001b".toByteArray())
        terminal.feed("\\ok".toByteArray())
        assertEquals("éok", line(terminal.frame(), 0).trimEnd())
    }

    @Test
    fun eraseBackspaceWrapAndBoundedHistory() {
        val terminal = AnsiTerminal(4, 2)
        terminal.feed("abcdX\bY\u001b[2KZ".toByteArray())
        assertEquals("abcd", line(terminal.frame(), 0))
        assertEquals(" Z", line(terminal.frame(), 1).trimEnd())
        repeat(500) { terminal.feed("\r\nline".toByteArray()) }
        assertTrue(terminal.frame().lines.size <= 202)
        terminal.feed("\u001b[2J\u001b[Hreset".toByteArray())
        assertTrue(terminal.frame().lines.any { it.first().text == 'r' })
    }

    private fun line(frame: TerminalFrame, row: Int) = frame.lines[row].joinToString("") {
        it.text.toString()
    }
}
