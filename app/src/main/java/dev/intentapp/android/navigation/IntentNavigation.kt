package dev.intentapp.android.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.assistant.AssistantRoute
import dev.intentapp.android.attachments.PhotoAttachmentStore
import dev.intentapp.android.cache.OfflineCache
import dev.intentapp.android.connection.SavedConnection
import dev.intentapp.android.drawers.WorkspaceDrawers
import dev.intentapp.android.git.LocalWorkspaceRepository
import dev.intentapp.android.git.repositoryLabel
import dev.intentapp.android.history.LocalHistoryAgent
import dev.intentapp.android.history.LocalHistoryNavigation
import dev.intentapp.android.workspaces.CreationDraftStore
import dev.intentapp.android.workspaces.CreationMediaSource
import dev.intentapp.android.workspaces.CreationWorkflow
import dev.intentapp.android.workspaces.LocalOpenRecentWorkspaceAgent
import dev.intentapp.android.workspaces.RpcWorkspaceOperations
import dev.intentapp.android.workspaces.RpcWorkspaceSession
import dev.intentapp.android.workspaces.WorkspaceAction
import dev.intentapp.android.workspaces.WorkspaceActionDialog
import dev.intentapp.android.workspaces.WorkspaceActionStore
import dev.intentapp.android.workspaces.WorkspaceCreateScreen
import dev.intentapp.android.workspaces.WorkspaceListRoute
import dev.intentapp.android.workspaces.WorkspaceStore
import dev.intentapp.android.workspaces.WorkspaceTransferScreen

// Connection-scoped navigation state and destinations.
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun IntentNavigation(
    session: RpcSession,
    modifier: Modifier = Modifier,
    cache: OfflineCache? = null,
    destinations: List<SavedConnection> = emptyList(),
    initialWorkspace: String? = null,
    connectionScope: String = "",
    workspaceContent: @Composable (String, () -> Unit) -> Unit
) {
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
    val history = LocalHistoryNavigation.current
    var historySelection by rememberSaveable(session) { mutableStateOf(0L) }
    var historyAgent by rememberSaveable(session) { mutableStateOf<String?>(null) }
    LaunchedEffect(history?.target, session) {
        history?.target?.takeIf { it.connectionId == history.connectionId }?.let {
            historySelection += 1
            historyAgent = it.agentId
            openRecent = false
            selectedWorkspace = it.workspaceId
            history.consumeTarget()
        }
    }
    LaunchedEffect(lifecycleState.removedId) {
        lifecycleState.removedId?.let { id ->
            history?.connectionId?.let { history.store.removeWorkspace(it, id) }
        }
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
    val context = LocalContext.current.applicationContext
    val creation = remember(session, connectionScope) {
        val drafts = connectionScope.takeIf {
            it.isNotBlank()
        }?.let { CreationDraftStore(context, it) }
        CreationWorkflow(
            RpcWorkspaceOperations(session),
            drafts?.load() ?: dev.intentapp.android.workspaces.WorkspaceDraft(),
            { drafts?.save(it) },
            { AttachmentUploadService(RpcAttachmentClient(session), it) }
        )
    }
    val creationPhotos =
        remember(session) { PhotoAttachmentStore(CreationMediaSource(context, session)) }
    var assistant by rememberSaveable(session) { mutableStateOf(false) }
    val connectionReady = LocalConnectionReady.current
    val authority = remember(session) {
        HostAuthorityStore {
            session.request("principal.me").thenApply { principal ->
                dev.intentapp.android.assistant.assistantOwner(
                    principal,
                    session.serverCapabilities
                )
            }
        }
    }
    val assistantOwner by authority.owner.collectAsState()
    LaunchedEffect(authority, connectionReady) { authority.setConnected(connectionReady) }
    DisposableEffect(authority) { onDispose { authority.close() } }
    val selected = selectedWorkspace
    val back = {
        if (!operationBusy) {
            when {
                assistant -> assistant = false
                creating -> creating = false
                transferring -> transferring = false
                else -> selectedWorkspace = null
            }
        }
    }
    BackHandler(selected != null || creating || assistant || transferring, onBack = back)
    val canTransfer = connectionReady && assistantOwner
    val workspaceAction = ChromeAction("Transfer workspace", !operationBusy && canTransfer) {
        transferring = true
    }
    val selectedRow = workspaceState.workspaces.firstOrNull { it.id == selected }
    val lifecycleActions = selectedRow?.let { workspace ->
        WorkspaceAction.entries.map { action ->
            ChromeAction(
                "${action.label} workspace",
                !lifecycleState.busy,
                destructive =
                action == WorkspaceAction.Delete
            ) {
                lifecycle.begin(workspace, action)
            }
        }
    }.orEmpty()
    WorkspaceActionDialog(lifecycleState, lifecycle)
    CompositionLocalProvider(
        LocalOpenWorkspaceAgent provides { workspaceId, agentId ->
            historySelection += 1
            historyAgent = agentId
            openRecent = false
            selectedWorkspace = workspaceId
            assistant = false
            creating = false
            transferring = false
        },
        dev.intentapp.android.assistant.LocalOpenProposalWorkspace provides { id ->
            selectedWorkspace = id
            assistant = false
            creating = false
            store.refresh()
        },
        dev.intentapp.android.history.LocalHistoryWorkspaceTitles provides
            workspaceState.workspaces.associate { it.id to it.title },
        LocalHistoryAgent provides historyAgent,
        dev.intentapp.android.history.LocalHistorySelection provides historySelection,
        LocalOpenRecentWorkspaceAgent provides openRecent,
        LocalLauncherActions provides LocalLauncherActions.current?.copy(
            onCreate = {
                assistant = false
                creating = true
            },
            onAssistant = if (assistantOwner) ({ assistant = true }) else null
        ),
        LocalChromeActions provides (lifecycleActions + listOf(workspaceAction)),
        LocalWorkspaceRepository provides
            repositoryLabel(workspaceState.workspaces.firstOrNull { it.id == selected }),
        LocalWorkspaceDetails provides workspaceState.workspaces.firstOrNull { it.id == selected },
        LocalWorkspaceTitle provides
            (workspaceState.workspaces.firstOrNull { it.id == selected }?.title ?: "Workspace")
    ) {
        Column(modifier) {
            when {
                assistant -> AssistantRoute(session, connectionScope, {
                    assistant =
                        false
                }) {
                    selectedWorkspace = it
                    assistant = false
                    store.refresh()
                }
                creating -> {
                    WorkspaceCreateScreen(creation, onBusy = { operationBusy = it }, onCreated = {
                        selectedWorkspace = it
                        creating = false
                    }, photos = creationPhotos, onClose = back)
                }
                selected == null -> WorkspaceDrawers {
                    WorkspaceListRoute(
                        store,
                        {
                            historyAgent = null
                            openRecent = false
                            selectedWorkspace = it
                        },
                        onOpenRecent = {
                            historyAgent = null
                            openRecent = true
                            selectedWorkspace = it
                        },
                        onAction = lifecycle::begin,
                        updatingId = lifecycleState.workspace?.id.takeIf { lifecycleState.busy }
                    )
                }
                transferring -> {
                    WorkspaceTransferScreen(
                        session,
                        selected,
                        destinations,
                        onBusy = {
                            operationBusy =
                                it
                        },
                        onDone = { transferring = false },
                        workspaceTitle = selectedRow?.title ?: "Workspace",
                        sourceState = if (LocalConnectionReady.current) {
                            dev.intent.core.protocol.session.SessionState.READY
                        } else {
                            dev.intent.core.protocol.session.SessionState.DISCONNECTED
                        }
                    )
                }
                else -> workspaceContent(selected) { selectedWorkspace = null }
            }
        }
    }
}
