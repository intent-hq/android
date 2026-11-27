package dev.intentapp.android.terminals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentTextButton as TextButton
import dev.intentapp.android.design.intentPageInsets
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.CompactAppBar

@Suppress("LongParameterList", "LongMethod") // Create and open requests from the workspace.
@Composable
fun WorkspaceTerminals(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    createOnOpen: Boolean = false,
    onCreateHandled: () -> Unit = {},
    openTerminal: String? = null,
    onOpenHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember(session, workspaceId) {
        TerminalStore(
            RpcTerminalSession(session, workspaceId),
            SavedTerminalNames(context.getSharedPreferences("terminal-names", 0), workspaceId)
        )
    }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    val state by store.state.collectAsState()
    TerminalCreationEffect(store, state, createOnOpen, onCreateHandled)
    // A terminal opened from the Agents page closes straight back to it.
    var direct by remember(store) { mutableStateOf(openTerminal != null) }
    LaunchedEffect(store, openTerminal, state.loading, state.terminals) {
        val listed = state.terminals.any { it.id == openTerminal }
        if (openTerminal != null && !state.loading && listed) {
            store.select(openTerminal)
            onOpenHandled()
        }
    }
    val back = {
        if (state.selected != null && !direct) store.select(null) else onBack()
        direct = false
    }
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
            TerminalListScreen(
                state,
                store::refresh,
                store::select,
                TerminalListActions(store::create, store::delete, store::rename)
            )
        } else {
            Column(
                Modifier.weight(1f).intentPageInsets().imePadding(),
                verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
            ) {
                state.error?.let { Text(it, color = IntentScreen.error) }
                state.actionError?.let { Text(it, color = IntentScreen.error) }
                state.notice?.let { Text(it) }
                if (state.loading) Text("Loading terminals…")
                Text(
                    state.terminals.firstOrNull { it.id == state.selected }?.let(state::title)
                        ?: "Terminal"
                )
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
    var expanded by remember(canvas) { mutableStateOf(false) }
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
            TerminalKey("Keyboard", state.ready, canvas::toggleKeyboard)
            TerminalKey(if (expanded) "Less keys" else "More keys", true) { expanded = !expanded }
        }
        if (expanded) {
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("terminal-extra-keys")) {
                TerminalSymbolKeys(canvas, state.ready)
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
}

@Composable
private fun TerminalSymbolKeys(canvas: TerminalCanvas, enabled: Boolean) {
    listOf(
        Triple(R.drawable.ic_arrow_left, "Left", 'D'),
        Triple(R.drawable.ic_arrow_up, "Up", 'A'),
        Triple(R.drawable.ic_arrow_down, "Down", 'B'),
        Triple(R.drawable.ic_arrow_right, "Right", 'C')
    ).forEach { (icon, label, code) ->
        TextButton(
            enabled = enabled,
            onClick = { canvas.arrow(code) },
            modifier = Modifier.focusProperties { canFocus = false }
        ) { Icon(painterResource(icon), label, Modifier.size(18.dp)) }
    }
    listOf("~", "|", "/", "-", "_", "$", "Enter").forEach { key ->
        TerminalKey(key, enabled) {
            canvas.input(
                if (key ==
                    "Enter"
                ) {
                    "\r"
                } else {
                    key
                }
            )
        }
    }
}

@Composable
private fun TerminalKey(label: String, enabled: Boolean, action: () -> Unit) {
    TextButton(
        enabled = enabled,
        onClick = action,
        modifier = Modifier.focusProperties { canFocus = false }
    ) { Text(label) }
}

private const val DEFAULT_FONT_SIZE = 12
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 24
