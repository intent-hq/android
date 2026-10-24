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
import dev.intent.app.connection.SavedConnection
import dev.intent.app.workspaces.CreationOptionsStore
import dev.intent.app.workspaces.RpcWorkspaceOperations
import dev.intent.app.workspaces.RpcWorkspaceSession
import dev.intent.app.workspaces.WorkspaceCreateScreen
import dev.intent.app.workspaces.WorkspaceListRoute
import dev.intent.app.workspaces.WorkspaceStore
import dev.intent.app.workspaces.WorkspaceTransferScreen
import dev.intent.core.protocol.session.RpcSession

/** The connection owner supplies a paired session when the connection flow is available. */
@Suppress("LongParameterList")
@Composable
fun IntentNavigation(
    session: RpcSession? = null,
    modifier: Modifier = Modifier,
    cache: OfflineCache? = null,
    destinations: List<SavedConnection> = emptyList(),
    initialWorkspace: String? = null,
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
    var selectedWorkspace by remember(session, initialWorkspace) {
        mutableStateOf(initialWorkspace)
    }
    DisposableEffect(store) { onDispose { store.close() } }
    var creating by remember(session) { mutableStateOf(false) }
    var transferring by remember(session) { mutableStateOf(false) }
    var operationBusy by remember(session) { mutableStateOf(false) }
    val creation = remember(session) { CreationOptionsStore(RpcWorkspaceOperations(session)) }
    val selected = selectedWorkspace
    BackHandler(selected != null) { if (!operationBusy) selectedWorkspace = null }
    Column(modifier) {
        if (selected == null) {
            Button(onClick = {
                creating = !creating
            }, enabled = !operationBusy) {
                Text(if (creating) "Back to workspaces" else "Create workspace")
            }
            if (creating) {
                WorkspaceCreateScreen(creation, onBusy = { operationBusy = it }) {
                    selectedWorkspace = it
                    creating = false
                }
            } else {
                WorkspaceListRoute(store, { selectedWorkspace = it })
            }
        } else {
            Button(onClick = {
                transferring = !transferring
            }, enabled = !operationBusy) {
                Text(if (transferring) "Back to workspace" else "Transfer workspace")
            }
            if (transferring) {
                WorkspaceTransferScreen(session, selected, destinations, onBusy = {
                    operationBusy =
                        it
                })
            } else {
                workspaceContent(selected) { selectedWorkspace = null }
            }
        }
    }
}
