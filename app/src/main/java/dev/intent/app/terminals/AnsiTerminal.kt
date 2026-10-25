package dev.intent.app.terminals

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

data class TerminalCell(val text: Char = ' ', val foreground: Int = 7, val background: Int = 0)
data class TerminalFrame(
    val lines: List<List<TerminalCell>> = emptyList(),
    val cursorRow: Int = 0,
    val cursorColumn: Int = 0
)

/** Bounded ANSI display: UTF-8, basic SGR colors, cursor movement, erase, CR/LF and backspace. */
@Suppress("MagicNumber", "TooManyFunctions")
class AnsiTerminal(private var columns: Int = 80, private var rows: Int = 24) {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(
            CodingErrorAction.REPLACE
        ).onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = byteArrayOf()
    private var cells = mutableListOf<MutableList<TerminalCell>>()
    private var x = 0
    private var y = 0
    private var foreground = 7
    private var background = 0
    private var mode = Mode.TEXT
    private val escape = StringBuilder()
    private var saved = 0 to 0

    init {
        reset()
    }

    fun reset() {
        decoder.reset()
        pending = byteArrayOf()
        cells = MutableList(rows) { blank() }
        x = 0
        y = 0
        foreground = 7
        background = 0
        mode = Mode.TEXT
        escape.clear()
    }

    fun resize(columns: Int, rows: Int) {
        this.columns = columns.coerceIn(1, 240)
        this.rows = rows.coerceIn(1, 100)
        cells.forEach { line ->
            while (line.size < this.columns) line.add(TerminalCell())
            while (line.size > this.columns) line.removeAt(line.lastIndex)
        }
        while (cells.size < this.rows) cells.add(blank())
        x = x.coerceAtMost(this.columns - 1)
        y = y.coerceAtMost(cells.lastIndex)
    }

    fun feed(bytes: ByteArray) {
        val input = ByteBuffer.wrap(pending + bytes)
        val output = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, output, false)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        output.flip()
        while (output.hasRemaining()) consume(output.get())
    }

    fun frame() = TerminalFrame(cells.map { it.toList() }, y, x.coerceAtMost(columns - 1))

    private fun consume(char: Char) {
        when (mode) {
            Mode.TEXT -> text(char)
            Mode.ESCAPE -> escape(char)
            Mode.CSI -> {
                if (char in '@'..'~') {
                    command(char)
                    escape.clear()
                    mode = Mode.TEXT
                } else if (escape.length < 128) {
                    escape.append(char)
                } else {
                    escape.clear()
                    mode = Mode.TEXT
                }
            }
            Mode.OSC -> if (char ==
                '\u0007'
            ) {
                mode = Mode.TEXT
            } else if (char == '\u001b') {
                mode = Mode.OSC_END
            }
            Mode.OSC_END -> mode = if (char == '\\') Mode.TEXT else Mode.OSC
        }
    }

    private fun escape(char: Char) {
        mode = when (char) {
            '[' -> Mode.CSI
            ']' -> Mode.OSC
            else -> Mode.TEXT
        }
        when (char) {
            '7' -> saved = x to y
            '8' -> {
                x = saved.first.coerceIn(0, columns - 1)
                y =
                    saved.second.coerceIn(0, cells.lastIndex)
            }
            'c' -> reset()
        }
    }

    private fun text(char: Char) {
        when (char) {
            '\u001b' -> mode = Mode.ESCAPE
            '\r' -> x = 0
            '\n' -> newline()
            '\b' -> x = (x - 1).coerceAtLeast(0)
            '\t' -> x = ((x / 8 + 1) * 8).coerceAtMost(columns - 1)
            else -> if (char >= ' ' && char != '\u007f') {
                if (x >= columns) {
                    x = 0
                    newline()
                }
                cells[y][x++] = TerminalCell(char, foreground, background)
            }
        }
    }

    private fun newline() {
        y++
        if (y >= cells.size) cells.add(blank())
        if (cells.size > rows + 200) {
            cells.removeAt(0)
            y--
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun command(final: Char) {
        if (escape.startsWith("?")) return
        val values = escape.toString().split(';').map { it.toIntOrNull() ?: 0 }
        val count = values.first().coerceAtLeast(1)
        val top = (cells.size - rows).coerceAtLeast(0)
        when (final) {
            'A' -> y = (y - count).coerceAtLeast(top)
            'B' -> y = (y.toLong() + count).coerceAtMost(cells.lastIndex.toLong()).toInt()
            'C' -> x = (x.toLong() + count).coerceAtMost((columns - 1).toLong()).toInt()
            'D' -> x = (x - count).coerceAtLeast(0)
            'G' -> x = (count - 1).coerceAtMost(columns - 1)
            'H', 'f' -> {
                y = (top.toLong() + count - 1).coerceAtMost(cells.lastIndex.toLong()).toInt()
                x = ((values.getOrNull(1) ?: 1).coerceAtLeast(1) - 1).coerceAtMost(columns - 1)
            }
            'J' -> eraseDisplay(values.first())
            'K' -> eraseLine(values.first())
            'm' -> colors(values)
            's' -> saved = x to y
            'u' -> {
                x = saved.first.coerceIn(0, columns - 1)
                y =
                    saved.second.coerceIn(0, cells.lastIndex)
            }
        }
    }

    private fun eraseLine(kind: Int) {
        val range = when (kind) {
            1 -> 0..x.coerceAtMost(columns - 1)
            2 -> 0 until columns
            else ->
                x until
                    columns
        }
        range.forEach { cells[y][it] = TerminalCell(' ', foreground, background) }
    }

    private fun eraseDisplay(kind: Int) {
        when (kind) {
            2, 3 -> cells.indices.forEach { cells[it] = blank() }
            1 -> {
                for (index in 0 until y) cells[index] = blank()
                eraseLine(1)
            }
            else -> {
                for (index in y + 1 until cells.size) cells[index] = blank()
                eraseLine(0)
            }
        }
    }

    private fun colors(values: List<Int>) {
        values.forEach {
            when (it) {
                0 -> {
                    foreground = 7
                    background = 0
                }
                1 -> if (foreground < 8) foreground += 8
                22 -> if (foreground >= 8) foreground -= 8
                in 30..37 -> foreground = it - 30
                39 -> foreground = 7
                in 40..47 -> background = it - 40
                49 -> background = 0
                in 90..97 -> foreground = it - 90 + 8
                in 100..107 -> background = it - 100 + 8
            }
        }
    }

    private fun blank() = MutableList(columns) { TerminalCell() }
    private enum class Mode { TEXT, ESCAPE, CSI, OSC, OSC_END }
}
