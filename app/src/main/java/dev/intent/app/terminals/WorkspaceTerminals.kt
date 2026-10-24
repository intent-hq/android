package dev.intent.app.terminals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceTerminals(session: RpcSession, workspaceId: String, onBack: () -> Unit) {
    val store =
        remember(session, workspaceId) { TerminalStore(RpcTerminalSession(session, workspaceId)) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    val state by store.state.collectAsState()
    val back = { if (state.selected != null) store.select(null) else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            TextButton(onClick = back) { Text("Back") }
            TextButton(onClick = store::refresh) { Text("Refresh") }
        }
        Text("Terminals", style = MaterialTheme.typography.titleLarge)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it) }
        if (state.loading) Text("Loading terminals…")
        if (state.selected == null) {
            if (!state.loading && state.terminals.isEmpty()) Text("No terminals in this workspace.")
            LazyColumn {
                items(state.terminals, key = { it.id }) { terminal ->
                    TextButton(onClick = { store.select(terminal.id) }) {
                        Text("${terminal.name}\n${terminal.cwd}")
                    }
                }
            }
        } else {
            Text(state.terminals.firstOrNull { it.id == state.selected }?.name ?: "Terminal")
            Text("Basic ANSI display. Full-screen apps have limited rendering.")
            TerminalOutput(state.frame, store::resize, Modifier.weight(1f))
            if (state.ended) Text("This terminal has ended.")
            TerminalInput(state.ready && !state.sending, state.selected, store::send)
        }
    }
}

@Composable
private fun TerminalInput(enabled: Boolean, terminalId: String?, send: (String) -> Unit) {
    var input by remember(terminalId) { mutableStateOf("") }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Terminal input") },
        enabled = enabled
    )
    Row {
        Button(enabled = enabled, onClick = {
            send(input + "\r")
            input = ""
        }) { Text("Send ↵") }
        TextButton(enabled = enabled, onClick = { send("\u0003") }) { Text("Ctrl-C") }
        TextButton(enabled = enabled, onClick = { send("\t") }) { Text("Tab") }
        TextButton(enabled = enabled, onClick = { send("\u001b") }) { Text("Esc") }
    }
}

@Composable
private fun TerminalOutput(frame: TerminalFrame, resize: (Int, Int) -> Unit, modifier: Modifier) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    val measurer = rememberTextMeasurer()
    val cell = remember(measurer) { measurer.measure("M", style).size }
    LazyColumn(
        modifier.onSizeChanged {
            resize(it.width / cell.width.coerceAtLeast(1), it.height / cell.height.coerceAtLeast(1))
        }.horizontalScroll(rememberScrollState())
    ) {
        items(frame.lines.size) { row ->
            Text(
                buildAnnotatedString {
                    frame.lines[row].forEachIndexed { column, cell ->
                        val cursor = row == frame.cursorRow && column == frame.cursorColumn
                        val foreground = if (cursor) cell.background else cell.foreground
                        val background = if (cursor) cell.foreground else cell.background
                        withStyle(
                            SpanStyle(
                                color = terminalColors[foreground],
                                background = terminalColors[background]
                            )
                        ) { append(cell.text) }
                    }
                },
                style = style,
                softWrap = false
            )
        }
    }
}

@Suppress("MagicNumber")
private val terminalColors = listOf(
    Color(0xFF000000), Color(0xFFAA0000), Color(0xFF00AA00), Color(0xFFAA5500),
    Color(0xFF0000AA), Color(0xFFAA00AA), Color(0xFF00AAAA), Color(0xFFAAAAAA),
    Color(0xFF555555), Color(0xFFFF5555), Color(0xFF55FF55), Color(0xFFFFFF55),
    Color(0xFF5555FF), Color(0xFFFF55FF), Color(0xFF55FFFF), Color(0xFFFFFFFF)
)
