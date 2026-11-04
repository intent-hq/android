package dev.intent.app.scripts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton
import dev.intent.app.navigation.LocalConnectionReady
import dev.intent.core.protocol.browser.PreviewUrl
import dev.intent.core.protocol.models.WorkspaceScriptResource
import dev.intent.core.protocol.models.WorkspaceScriptRuntime
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceScriptsRoute(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit
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
    val state by store.state.collectAsState()
    WorkspaceScriptsScreen(
        state.copy(connected = connectionReady && state.connected),
        onBack,
        store::refresh,
        store::setRunning,
        onOpenBrowser
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScriptsScreen(
    state: WorkspaceScriptsState,
    onBack: () -> Unit,
    refresh: () -> Unit,
    setRunning: (String, Boolean) -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = IntentScreen.pageInsets,
        verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
    ) {
        item {
            Column {
                Text("Scripts", style = MaterialTheme.typography.headlineSmall)
                Text("Under development", color = IntentScreen.secondaryText)
                FlowRow {
                    IntentTextButton(onClick = onBack) { Text("Back") }
                    IntentTextButton(
                        onClick = refresh,
                        enabled = state.connected && !state.loading
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item { Text(message, color = IntentScreen.error) } }
        if (!state.loading && state.scripts.isEmpty() && state.error == null) {
            item {
                IntentSection("No scripts yet") {
                    Text("Create a workspace script on the host to run it here.")
                }
            }
        }
        items(state.scripts, key = { it.id }) { script ->
            ScriptResourceRow(
                script,
                enabled = state.connected && !state.loading && script.id !in state.pending,
                pending = script.id in state.pending,
                setRunning = { setRunning(script.id, it) },
                onOpenBrowser = onOpenBrowser
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptResourceRow(
    script: WorkspaceScriptResource,
    enabled: Boolean,
    pending: Boolean,
    setRunning: (Boolean) -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    val runtime = script.runtime
    IntentSection(script.name.ifBlank { script.id }) {
        Text(script.command, style = MaterialTheme.typography.bodySmall)
        Text("${script.mode} · ${scriptStatusLabel(runtime)}", color = IntentScreen.secondaryText)
        runtime?.error?.let { Text(it, color = IntentScreen.error) }
        if (runtime?.previouslyRunning == true) Text("Stopped when the host restarted.")
        FlowRow {
            IntentTextButton(
                onClick = { setRunning(runtime?.isLive != true) },
                enabled = enabled && runtime != null
            ) {
                Text(
                    if (pending) {
                        "Updating…"
                    } else if (runtime?.isLive == true) {
                        "Stop"
                    } else {
                        "Start"
                    }
                )
            }
            val url = runtime?.detectedUrl?.takeIf {
                runtime.status == "running" && runCatching { PreviewUrl.parse(it) }.isSuccess
            }
            if (url != null) {
                IntentTextButton(onClick = {
                    onOpenBrowser(url)
                }, enabled = enabled) { Text("Open preview") }
            }
        }
    }
}

internal fun scriptStatusLabel(runtime: WorkspaceScriptRuntime?): String = when (runtime?.status) {
    null -> "Status unavailable"
    "idle" -> "Idle"
    "starting" -> "Starting"
    "running" -> "Running"
    "restarting" -> "Restarting"
    "exited" -> runtime.exitCode?.let { "Exited ($it)" } ?: "Exited"
    else -> runtime.status
}
