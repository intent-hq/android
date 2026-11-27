package dev.intentapp.android.scripts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.SectionHeadingPull
import dev.intentapp.android.design.pullNextItem
import dev.intentapp.android.lists.RefreshableList
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.CompactAppBar
import dev.intentapp.android.navigation.LocalConnectionReady

@Composable
fun WorkspaceScriptsRoute(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    WorkspaceScriptsHost(session, workspaceId, onOpenBrowser) { state, actions ->
        Column(Modifier.fillMaxSize()) {
            CompactAppBar(
                "Scripts",
                onBack,
                listOf(ChromeAction("Refresh", enabled = state.connected, run = actions.reload))
            )
            WorkspaceScriptsScreen(state, actions, Modifier.weight(1f))
        }
    }
}

/** Owns one scripts store, opens detected addresses, and shows the output sheet. */
@Composable
fun WorkspaceScriptsHost(
    session: RpcSession,
    workspaceId: String,
    onOpenBrowser: (String) -> Unit,
    content: @Composable (WorkspaceScriptsState, WorkspaceScriptsActions) -> Unit
) {
    val connectionReady = LocalConnectionReady.current
    val store = remember(session, workspaceId) {
        WorkspaceScriptsStore(RpcWorkspaceScriptSession(session, workspaceId), workspaceId)
    }
    DisposableEffect(store) {
        store.start(connectionReady)
        onDispose { store.close() }
    }
    LaunchedEffect(store, connectionReady) { store.connectionChanged(connectionReady) }
    val stored by store.state.collectAsState()
    val state = stored.copy(connected = connectionReady && stored.connected)
    LaunchedEffect(state.openUrl) {
        state.openUrl?.let {
            store.consumeOpenUrl()
            onOpenBrowser(it)
        }
    }
    val actions = remember(store) {
        WorkspaceScriptsActions(
            launch = store::launch,
            stop = store::stop,
            remove = store::remove,
            showOutput = store::showOutput,
            reload = store::reload
        )
    }
    content(state, actions)
    state.output?.let { output ->
        val row = state.rows.firstOrNull { it.id == output.scriptId }
        ScriptOutputSheet(
            output,
            row,
            state,
            ScriptOutputActions(
                dismiss = store::hideOutput,
                refresh = store::refreshOutput,
                launch = store::launch,
                stop = store::stop,
                openBrowser = onOpenBrowser
            )
        )
    }
}

/** iOS WorkspaceDetailResources: scripts that have run, listed below the workspace agents. */
fun LazyListScope.workspaceScriptsSection(
    state: WorkspaceScriptsState,
    actions: WorkspaceScriptsActions,
    revealed: String?,
    onReveal: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = state.rows.filter { it.hasRun }
    // Unlike iOS, a load error alone never adds the section (offline or older desktops).
    if (rows.isEmpty()) return
    item(key = "scripts.heading") {
        Box(modifier.pullNextItem(SectionHeadingPull)) { ScriptsEyebrow() }
    }
    state.error?.let { error ->
        item(key = "scripts.error") {
            Box(modifier) { ScriptsError(error, state.connected, actions.reload) }
        }
    }
    items(rows, key = { "script.${it.id}" }) { row ->
        Box(modifier) {
            ScriptRow(
                row,
                state,
                revealed = revealed == row.id,
                onReveal = { onReveal(if (it) row.id else null) },
                actions = actions
            )
        }
    }
}

/** iOS WorkspaceScriptsView: an uppercase SCRIPTS eyebrow over 36dp-spaced script rows. */
@Composable
fun WorkspaceScriptsScreen(
    state: WorkspaceScriptsState,
    actions: WorkspaceScriptsActions,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf<String?>(null) }
    val rows = state.rows
    RefreshableList(state.loading, actions.reload, modifier) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("scripts.list"),
            contentPadding = PaddingValues(vertical = IntentSpacing.content),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
        ) {
            item(key = "heading") { ScriptsEyebrow() }
            if (!state.loaded && state.loading && state.connected) {
                item(key = "loading") { PageText("Loading scripts…") }
            }
            state.error?.let { error ->
                item(key = "error") { ScriptsError(error, state.connected, actions.reload) }
            }
            if (state.loaded && rows.isEmpty() && state.error == null) {
                item(key = "empty") { ScriptsEmpty() }
            }
            items(rows, key = { it.id }) { row ->
                ScriptRow(
                    row,
                    state,
                    revealed = revealed == row.id,
                    onReveal = { revealed = if (it) row.id else null },
                    actions = actions
                )
            }
        }
    }
}

@Composable
private fun ScriptsEyebrow() {
    Text(
        "SCRIPTS",
        Modifier.padding(horizontal = IntentSpacing.page).semantics { heading() },
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.24.sp),
        color = IntentScreen.secondaryText
    )
}

@Composable
private fun PageText(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = IntentSpacing.page),
        style = MaterialTheme.typography.bodyMedium,
        color = IntentScreen.secondaryText
    )
}

/** iOS shows the error in muted bodyXS with a secondary Retry button. */
@Composable
private fun ScriptsError(error: String, connected: Boolean, retry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = IntentSpacing.page),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
    ) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentScreen.secondaryText
        )
        ScriptSecondaryButton("Retry", retry, connected, Modifier.testTag("scripts.retry"))
    }
}

@Composable
private fun ScriptsEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = IntentSpacing.page),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
    ) {
        Text("No scripts", style = MaterialTheme.typography.titleMedium)
        Text(
            "Scripts saved in this workspace and scripts in package.json, Makefile, " +
                "Cargo.toml or pyproject.toml appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentScreen.secondaryText
        )
    }
}

// iOS WorkspaceDetailStyle.rowSpacing.
private val ROW_SPACING = 36.dp
