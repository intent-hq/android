package dev.intent.app.terminals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.intentPageInsets
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceTerminals(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    createOnOpen: Boolean = false,
    onCreateHandled: () -> Unit = {}
) {
    val store =
        remember(session, workspaceId) { TerminalStore(RpcTerminalSession(session, workspaceId)) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    val state by store.state.collectAsState()
    TerminalCreationEffect(store, state, createOnOpen, onCreateHandled)
    val back = { if (state.selected != null) store.select(null) else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        CompactAppBar(
            "Terminals",
            back,
            if (state.selected ==
                null
            ) {
                emptyList()
            } else {
                listOf(ChromeAction("Refresh", run = store::refresh))
            }
        )
        if (state.selected == null) {
            TerminalListScreen(state, store::refresh, store::select, store::create, store::delete)
        } else {
            Column(
                Modifier.weight(1f).intentPageInsets().imePadding(),
                verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
            ) {
                state.error?.let { Text(it, color = IntentScreen.error) }
                state.actionError?.let { Text(it, color = IntentScreen.error) }
                state.notice?.let { Text(it) }
                if (state.loading) Text("Loading terminals…")
                Text(state.terminals.firstOrNull { it.id == state.selected }?.name ?: "Terminal")
                TerminalSurface(store, state, Modifier.weight(1f))
                if (state.ended) Text("This terminal has ended.")
            }
        }
    }
}

@Composable
private fun TerminalCreationEffect(
    store: TerminalStore,
    state: TerminalState,
    createOnOpen: Boolean,
    onCreateHandled: () -> Unit
) {
    var createHandled by remember(store, createOnOpen) { mutableStateOf(false) }
    LaunchedEffect(createOnOpen, state.loading) {
        if (createOnOpen && !createHandled && !state.loading) {
            createHandled = true
            onCreateHandled()
            if (state.connected) store.create()
        }
    }
}

@Composable
internal fun TerminalSurface(store: TerminalStore, state: TerminalState, modifier: Modifier) {
    val context = LocalContext.current
    val canvas = remember(store, state.selected) {
        TerminalCanvas(context).apply {
            terminal = store.terminal
            send = store::send
            resize = store::resize
        }
    }
    var control by remember(canvas) { mutableStateOf(false) }
    var fontSize by remember(canvas) { mutableStateOf(DEFAULT_FONT_SIZE) }
    Column(modifier) {
        AndroidView(
            factory = { canvas },
            modifier = Modifier.weight(1f),
            update = {
                it.inputEnabled = state.ready
                it.control = control
                it.controlChanged = { control = false }
                // Reading revision makes Compose invalidate the Canvas for every output batch.
                state.revision.let { _ -> it.invalidate() }
            }
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TerminalKey("Esc", state.ready) { canvas.input("\u001b") }
            TerminalKey("Ctrl${if (control) " ✓" else ""}", state.ready) { control = !control }
            TerminalKey("Tab", state.ready) { canvas.input("\t") }
            listOf("←" to 'D', "↑" to 'A', "↓" to 'B', "→" to 'C').forEach { (label, code) ->
                TerminalKey(label, state.ready) { canvas.arrow(code) }
            }
            listOf("~", "|", "/", "-", "_", "$", "Enter").forEach { key ->
                TerminalKey(key, state.ready) { canvas.input(if (key == "Enter") "\r" else key) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TerminalKey("Keyboard", state.ready, canvas::toggleKeyboard)
            TerminalKey("Copy output", true, canvas::copyOutput)
            TerminalKey("Paste", state.ready, canvas::paste)
            TerminalKey("A−", fontSize > MIN_FONT_SIZE) {
                fontSize--
                canvas.fontSize(fontSize)
            }
            TerminalKey("A+", fontSize < MAX_FONT_SIZE) {
                fontSize++
                canvas.fontSize(fontSize)
            }
        }
    }
}

@Composable
private fun TerminalKey(label: String, enabled: Boolean, action: () -> Unit) {
    TextButton(enabled = enabled, onClick = action) { Text(label) }
}

private const val DEFAULT_FONT_SIZE = 12
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 24
