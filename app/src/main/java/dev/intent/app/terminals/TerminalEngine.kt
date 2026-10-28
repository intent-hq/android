package dev.intent.app.terminals

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput

/** All access, including Canvas rendering, uses this lock: RPC callbacks can run off the UI thread. */
class TerminalEngine(private val reply: (String) -> Unit = {}) {
    private val responses = StringBuilder()
    private val output = object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            responses.append(String(data, offset, count, Charsets.UTF_8))
        }
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

        // Remote OSC clipboard requests never read or change the device clipboard.
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = Unit
    }
    private var emulator =
        TerminalEmulator(
            output,
            INITIAL_TERMINAL_COLUMNS,
            INITIAL_TERMINAL_ROWS,
            1,
            1,
            SCROLLBACK_ROWS,
            null
        )

    @Synchronized
    fun <T> read(block: (TerminalEmulator) -> T): T = block(emulator)

    @Synchronized
    fun reset() {
        emulator =
            TerminalEmulator(output, emulator.mColumns, emulator.mRows, 1, 1, SCROLLBACK_ROWS, null)
        responses.clear()
    }

    fun feed(bytes: ByteArray) = mutate { it.append(bytes, bytes.size) }

    @Synchronized
    fun resize(columns: Int, rows: Int) = emulator.resize(columns, rows, 1, 1)

    fun paste(text: String) = mutate { it.paste(text) }

    private fun mutate(block: (TerminalEmulator) -> Unit) {
        val response = synchronized(this) {
            block(emulator)
            responses.toString().also { responses.clear() }
        }
        if (response.isNotEmpty()) reply(response)
    }

    @Synchronized
    fun text(): String = emulator.screen.transcriptText
}

private const val SCROLLBACK_ROWS = 2000
