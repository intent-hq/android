package dev.intent.app.terminals

import com.termux.terminal.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEngineTest {
    @Test fun alternateScreenPreservesShellAndRestoresCursor() {
        val terminal = TerminalEngine()
        terminal.feed("shell$ \u001b[?1049h\u001b[2J\u001b[Hvim\u001b[2;3Hbody".toByteArray())
        terminal.read {
            assertTrue(it.isAlternateBufferActive)
            assertEquals("vim", it.getSelectedText(0, 0, 2, 0))
            assertEquals("body", it.getSelectedText(2, 1, 5, 1))
        }
        terminal.feed("\u001b[?1049l".toByteArray())
        assertEquals("shell$", terminal.text().trim())
        terminal.read { assertEquals(7, it.cursorCol) }
    }

    @Test fun colorsUnicodeAndEscapeSequencesSurviveChunkBoundaries() {
        val terminal = TerminalEngine()
        val bytes = "\u001b[38;5;196mR\u001b[38;2;12;34;56m色é\u001b[0m".toByteArray()
        bytes.forEach { terminal.feed(byteArrayOf(it)) }
        terminal.read {
            assertEquals("R色é", it.getSelectedText(0, 0, 3, 0))
            assertEquals(196, TextStyle.decodeForeColor(it.screen.getStyleAt(0, 0)))
            assertEquals(0xff0c2238.toInt(), TextStyle.decodeForeColor(it.screen.getStyleAt(0, 1)))
            assertEquals(4, it.cursorCol)
        }
    }

    @Test fun resizeScrollbackAndResetAreBoundedAndClearBothScreens() {
        val terminal = TerminalEngine()
        terminal.resize(20, 5)
        repeat(2200) { terminal.feed("line $it\r\n".toByteArray()) }
        terminal.read { assertTrue(it.screen.activeTranscriptRows <= 2000) }
        terminal.feed("\u001b[?1049hsecret".toByteArray())
        terminal.reset()
        assertEquals("", terminal.text().trim())
        terminal.read {
            assertFalse(it.isAlternateBufferActive)
            assertEquals(20, it.mColumns)
            assertEquals(5, it.mRows)
            assertEquals(0, it.screen.activeTranscriptRows)
        }
    }

    @Test fun inputEncodingAndBracketedPaste() {
        assertEquals("\u0003", TerminalKeys.text("c", true))
        assertEquals(
            "\u0000\u001b\u007f",
            TerminalKeys.text(
                " [ ?".replace(" ", "").let {
                    " $it"
                },
                true
            )
        )
        assertEquals("\u001bOA", TerminalKeys.arrow('A', true))
        assertEquals("\u001b[A", TerminalKeys.arrow('A', false))
        val writes = mutableListOf<String>()
        val terminal = TerminalEngine(writes::add)
        terminal.feed("\u001b[?2004h".toByteArray())
        terminal.paste("a\nb")
        assertEquals(listOf("\u001b[200~a\rb\u001b[201~"), writes)
    }

    @Test fun resizeUsesWholeCellsAndProtocolLimits() {
        assertEquals(80 to 24, TerminalKeys.size(800, 480, 10f, 20))
        assertEquals(2 to 2, TerminalKeys.size(0, 0, 0f, 0))
        assertEquals(240 to 100, TerminalKeys.size(10000, 10000, 1f, 1))
    }
}
