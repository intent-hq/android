package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.cache.OfflineCache
import dev.intent.app.workspaces.RpcWorkspaceSession
import dev.intent.app.workspaces.WorkspaceListRoute
import dev.intent.app.workspaces.WorkspaceStore
import dev.intent.core.protocol.session.RpcSession

/** The connection owner supplies a paired session when the connection flow is available. */
@Composable
fun IntentNavigation(
    session: RpcSession? = null,
    modifier: Modifier = Modifier,
    cache: OfflineCache? = null,
    workspaceContent: @Composable (String, () -> Unit) -> Unit = { _, back ->
        Column {
            Button(onClick = back) { Text("Back to workspaces") }
            Text("Workspace details are under development.")
        }
    }
) {
    if (session == null) {
        Column(modifier) {
            Text("Workspaces")
            Text(
                "The Android companion is under development. Connect a paired session to load workspaces."
            )
        }
        return
    }
    val store = remember(session, cache) { WorkspaceStore(RpcWorkspaceSession(session), cache) }
    var selectedWorkspace by remember(session) { mutableStateOf<String?>(null) }
    DisposableEffect(store) { onDispose { store.close() } }
    val selected = selectedWorkspace
    BackHandler(selected != null) { selectedWorkspace = null }
    if (selected == null) {
        WorkspaceListRoute(store, { selectedWorkspace = it }, modifier)
    } else {
        workspaceContent(selected) { selectedWorkspace = null }
    }
}
