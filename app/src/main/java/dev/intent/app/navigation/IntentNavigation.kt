package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.cache.OfflineCache
import dev.intent.app.connection.SavedConnection
import dev.intent.app.git.LocalWorkspaceRepository
import dev.intent.app.git.repositoryLabel
import dev.intent.app.workspaces.CreationOptionsStore
import dev.intent.app.workspaces.LocalOpenRecentWorkspaceAgent
import dev.intent.app.workspaces.RpcWorkspaceOperations
import dev.intent.app.workspaces.RpcWorkspaceSession
import dev.intent.app.workspaces.WorkspaceAction
import dev.intent.app.workspaces.WorkspaceActionDialog
import dev.intent.app.workspaces.WorkspaceActionStore
import dev.intent.app.workspaces.WorkspaceCreateScreen
import dev.intent.app.workspaces.WorkspaceListRoute
import dev.intent.app.workspaces.WorkspaceStore
import dev.intent.app.workspaces.WorkspaceTransferScreen
import dev.intent.core.protocol.session.RpcSession

/** The connection owner supplies a paired session when the connection flow is available. */
// Connection-scoped navigation state and destinations.
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
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
    val lifecycle = remember(store, session) {
        WorkspaceActionStore(store) { method, params -> session.request(method, params) }
    }
    val lifecycleState by lifecycle.state.collectAsState()
    DisposableEffect(lifecycle) { onDispose { lifecycle.close() } }
    var openRecent by rememberSaveable(session) { mutableStateOf(false) }
    var selectedWorkspace by rememberSaveable(session, initialWorkspace) {
        mutableStateOf(initialWorkspace)
    }
    LaunchedEffect(lifecycleState.removedId) {
        if (lifecycleState.removedId == selectedWorkspace) selectedWorkspace = null
    }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    val workspaceState by store.state.collectAsState()
    var creating by rememberSaveable(session) { mutableStateOf(false) }
    var transferring by rememberSaveable(session) { mutableStateOf(false) }
    var operationBusy by remember(session) { mutableStateOf(false) }
    val creation = remember(session) { CreationOptionsStore(RpcWorkspaceOperations(session)) }
    val selected = selectedWorkspace
    val back = {
        if (!operationBusy) {
            when {
                creating -> creating = false
                transferring -> transferring = false
                else -> selectedWorkspace = null
            }
        }
    }
    BackHandler(selected != null || creating, onBack = back)
    val inheritedActions = LocalChromeActions.current
    val workspaceAction = if (selected == null) {
        ChromeAction("Create workspace", !operationBusy) { creating = true }
    } else {
        ChromeAction("Transfer workspace", !operationBusy) { transferring = true }
    }
    val selectedRow = workspaceState.workspaces.firstOrNull { it.id == selected }
    val lifecycleActions = selectedRow?.let { workspace ->
        WorkspaceAction.entries.map { action ->
            ChromeAction("${action.label} workspace", !lifecycleState.busy) {
                lifecycle.begin(workspace, action)
            }
        }
    }.orEmpty()
    WorkspaceActionDialog(lifecycleState, lifecycle)
    CompositionLocalProvider(
        LocalOpenRecentWorkspaceAgent provides openRecent,
        LocalChromeActions provides (lifecycleActions + listOf(workspaceAction) + inheritedActions),
        LocalWorkspaceRepository provides
            repositoryLabel(workspaceState.workspaces.firstOrNull { it.id == selected }),
        LocalWorkspaceDetails provides workspaceState.workspaces.firstOrNull { it.id == selected },
        LocalWorkspaceTitle provides
            (workspaceState.workspaces.firstOrNull { it.id == selected }?.title ?: "Workspace")
    ) {
        Column(modifier) {
            when {
                creating -> {
                    CompactAppBar("Create workspace", onBack = back)
                    WorkspaceCreateScreen(creation, onBusy = { operationBusy = it }) {
                        selectedWorkspace = it
                        creating = false
                    }
                }
                selected == null -> WorkspaceListRoute(
                    store,
                    {
                        openRecent = false
                        selectedWorkspace = it
                    },
                    onOpenRecent = {
                        openRecent = true
                        selectedWorkspace = it
                    },
                    onAction = lifecycle::begin,
                    updatingId = lifecycleState.workspace?.id.takeIf { lifecycleState.busy }
                )
                transferring -> {
                    CompactAppBar("Transfer workspace", onBack = back)
                    WorkspaceTransferScreen(session, selected, destinations, onBusy = {
                        operationBusy =
                            it
                    })
                }
                else -> workspaceContent(selected) { selectedWorkspace = null }
            }
        }
    }
}
