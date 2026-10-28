package dev.intent.app.terminals

/** Ctrl is a one-shot modifier, matching the iOS key bar. */
object TerminalKeys {
    fun text(text: String, control: Boolean): String = if (control) {
        text.map { char ->
            when (char) {
                in 'a'..'z' -> (char.code - 'a'.code + 1).toChar()
                in '@'..'_' -> (char.code - '@'.code).toChar()
                ' ' -> '\u0000'
                '?' -> '\u007f'
                else -> char
            }
        }.joinToString("")
    } else {
        text.replace("\n", "\r")
    }

    fun arrow(final: Char, applicationMode: Boolean): String =
        "\u001b${if (applicationMode) 'O' else '['}$final"

    fun size(width: Int, height: Int, cellWidth: Float, cellHeight: Int): Pair<Int, Int> =
        (width / cellWidth.coerceAtLeast(1f)).toInt().coerceIn(2, MAX_COLUMNS) to
            (height / cellHeight.coerceAtLeast(1)).coerceIn(2, MAX_ROWS)
}

private const val MAX_COLUMNS = 240
private const val MAX_ROWS = 100
