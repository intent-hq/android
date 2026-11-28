package dev.intentapp.android.conversation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.R
import dev.intentapp.android.agents.AgentActionDialog
import dev.intentapp.android.agents.AgentControls
import dev.intentapp.android.agents.AgentListResources
import dev.intentapp.android.agents.AgentListRoute
import dev.intentapp.android.agents.AgentModelDialog
import dev.intentapp.android.agents.AgentStore
import dev.intentapp.android.agents.LocalConversationAgent
import dev.intentapp.android.agents.NewAgentOptionsStore
import dev.intentapp.android.agents.NewAgentScreen
import dev.intentapp.android.agents.RpcAgentSession
import dev.intentapp.android.attachments.AndroidAttachmentSource
import dev.intentapp.android.attachments.PhotoAttachmentStore
import dev.intentapp.android.browser.LocalWorkspaceBrowserLink
import dev.intentapp.android.browser.WorkspaceBrowsersHost
import dev.intentapp.android.browser.workspaceBrowsersSection
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.horizontalBleed
import dev.intentapp.android.drawers.LocalCurrentHistoryAgent
import dev.intentapp.android.drawers.LocalDrawerButtons
import dev.intentapp.android.drawers.WorkspaceDrawers
import dev.intentapp.android.navigation.ChromeAction
import dev.intentapp.android.navigation.LocalChromeActions
import dev.intentapp.android.navigation.LocalChromeBack
import dev.intentapp.android.scripts.WorkspaceScriptsHost
import dev.intentapp.android.scripts.workspaceScriptsSection
import dev.intentapp.android.status.AgentWatchStore
import dev.intentapp.android.status.ConversationStatusStrip
import dev.intentapp.android.status.LocalWatchState
import dev.intentapp.android.status.RpcAgentWatchSource
import dev.intentapp.android.status.RpcWatchSource
import dev.intentapp.android.status.ScriptWatchSheet
import dev.intentapp.android.status.WatchFamily
import dev.intentapp.android.status.WatchStore
import dev.intentapp.android.terminals.WorkspaceTerminalsHost
import dev.intentapp.android.terminals.workspaceTerminalsSection
import dev.intentapp.android.workspaces.RpcWorkspaceOperations
import dev.intentapp.android.workspaces.recentWorkspaceAgentEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Route lifetime and RPC error presentation.
@Suppress(
    "LongMethod",
    "TooGenericExceptionCaught",
    "CyclomaticComplexMethod",
    "LongParameterList"
)
@Composable
fun WorkspaceConversations(
    session: RpcSession,
    workspaceId: String,
    onBack: () -> Unit,
    connectionScope: String = "",
    browserConnectionId: String = connectionScope,
    onOpenTerminal: (String) -> Unit = {}
) {
    val watches =
        remember(session, workspaceId) { WatchStore(RpcWatchSource(session, workspaceId)) }
    val watchState by watches.state.collectAsState()
    DisposableEffect(watches) {
        watches.start()
        onDispose { watches.close() }
    }
    val listState = rememberSaveableStateHolder()
    val agents =
        remember(session, workspaceId) {
            AgentStore(RpcAgentSession(session, workspaceId), workspaceId)
        }
    val operations = remember(session) { RpcWorkspaceOperations(session) }
    val controls = remember(operations, workspaceId) { AgentControls(operations, workspaceId) }
    val scope = rememberCoroutineScope()
    var muteError by remember(session, workspaceId) { mutableStateOf<String?>(null) }
    var muting by remember(session, workspaceId) { mutableStateOf(false) }
    val historyAgent = dev.intentapp.android.history.LocalHistoryAgent.current
    var selected by rememberSaveable(session, workspaceId) { mutableStateOf(historyAgent) }
    LaunchedEffect(historyAgent, dev.intentapp.android.history.LocalHistorySelection.current) {
        if (historyAgent != null) selected = historyAgent
    }
    var creating by rememberSaveable(session, workspaceId) { mutableStateOf(false) }
    val creation = remember(session) { NewAgentOptionsStore(RpcWorkspaceOperations(session)) }
    val recentError = recentWorkspaceAgentEntry(session, workspaceId, selected, creating) {
        selected = it
    }
    DisposableEffect(agents) {
        agents.start()
        onDispose { agents.close() }
    }
    BackHandler(selected != null) { selected = null }
    Column {
        recentError?.let { Text(it) }
        val agentId = selected
        if (creating) {
            NewAgentScreen(creation, workspaceId, { creating = false }) {
                creating = false
                agents.refresh()
                selected = it
            }
        } else if (agentId == null) {
            muteError?.let { Text(it, color = IntentScreen.error) }
            CompositionLocalProvider(
                LocalWatchState provides watchState,
                LocalChromeBack provides onBack,
                LocalChromeActions provides
                    (
                        listOf(
                            ChromeAction("Create New Agent", icon = R.drawable.ic_add) {
                                creating =
                                    true
                            }
                        ) +
                            LocalChromeActions.current
                        )
            ) {
                listState.SaveableStateProvider("agents") {
                    WorkspaceAgentResources(
                        session,
                        workspaceId,
                        browserConnectionId,
                        onOpenTerminal
                    ) { resources ->
                        AgentListRoute(agents, { selected = it }, onMute = { agent ->
                            if (!muting) {
                                muting = true
                                muteError = null
                                scope.launch {
                                    try {
                                        val updated =
                                            withContext(Dispatchers.IO) { controls.mute(agent) }
                                        agents.changed(updated)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        muteError =
                                            failure.message
                                                ?: "Notification setting was not saved."
                                    } finally {
                                        muting = false
                                    }
                                }
                            }
                        }, resources = resources)
                    }
                }
            }
        } else {
            val proposals = remember(session, workspaceId, agentId) {
                dev.intentapp.android.assistant.ProposalStore(
                    dev.intentapp.android.assistant.proposalRequest(
                        session
                    ),
                    workspaceId,
                    agentId,
                    {
                        session.serverCapabilities
                    }
                )
            }
            DisposableEffect(proposals) { onDispose { proposals.close() } }
            val conversation = remember(session, workspaceId, agentId) {
                ConversationStore(RpcConversationSession(session, workspaceId, agentId), agentId)
            }
            val agentWatches = remember(session, workspaceId, agentId) {
                AgentWatchStore(RpcAgentWatchSource(session, workspaceId, agentId), agentId)
            }
            val waiting by agentWatches.state.collectAsState()
            DisposableEffect(agentWatches) {
                agentWatches.start()
                onDispose { agentWatches.close() }
            }
            val sends = remember(session, workspaceId, agentId) {
                ConversationSendStore(
                    RpcConversationCommands(session, workspaceId, agentId),
                    agentId
                )
            }
            val context = LocalContext.current.applicationContext
            val draft = remember(connectionScope, workspaceId, agentId) {
                if (connectionScope.isBlank()) {
                    ComposerDraft()
                } else {
                    ConversationDraftStore(context, connectionScope).draft(workspaceId, agentId)
                }
            }
            val seen = remember(session, workspaceId, agentId) {
                ConversationSeen({
                    markConversationSeen(session, workspaceId, agentId, it)
                }, ::scheduleSeen)
            }
            val photos = remember(session, workspaceId, agentId) {
                PhotoAttachmentStore(
                    AndroidAttachmentSource(
                        context,
                        AttachmentUploadService(RpcAttachmentClient(session), workspaceId)
                    )
                )
            }
            DisposableEffect(conversation, sends, photos) {
                onDispose {
                    conversation.close()
                    sends.close()
                    photos.close()
                }
            }
            val agentState by agents.state.collectAsState()
            val agent = agentState.agents.firstOrNull { it.id == agentId }
            dev.intentapp.android.history.RecordAgentVisit(workspaceId, agent)
            var action by remember(agentId) { mutableStateOf<String?>(null) }
            if (agent != null) {
                when (action) {
                    "Model settings" -> AgentModelDialog(
                        agent,
                        operations,
                        controls,
                        agents::changed
                    ) {
                        action =
                            null
                    }
                    "Rename agent", "Delete agent" -> AgentActionDialog(
                        action!!,
                        agent,
                        controls,
                        agents::changed,
                        onDeleted = {
                            agents.removed(agentId)
                            selected = null
                        },
                        onDismiss = { action = null }
                    )
                }
            }
            CompositionLocalProvider(
                dev.intentapp.android.assistant.LocalProposalStore provides proposals,
                LocalCurrentHistoryAgent provides agentId,
                LocalConversationAgent provides agentState.agents.firstOrNull {
                    it.id == agentId
                }
            ) {
                WorkspaceDrawers(historyOnly = true) {
                    Column {
                        ConversationHeader(
                            agentState.agents.firstOrNull { it.id == agentId }?.name
                                ?: "Conversation",
                            subtitle = "Agent",
                            onBack = { selected = null },
                            actions = listOfNotNull(
                                LocalDrawerButtons.current?.let { buttons ->
                                    ChromeAction(
                                        "Agent history",
                                        icon = R.drawable.ic_message_list
                                    ) {
                                        buttons.history()
                                    }
                                },
                                ChromeAction(
                                    "Select Model",
                                    enabled = agent != null,
                                    icon = R.drawable.ic_settings,
                                    group = true
                                ) { action = "Model settings" },
                                ChromeAction(
                                    "Rename",
                                    enabled = agent != null,
                                    icon = R.drawable.ic_edit
                                ) { action = "Rename agent" },
                                ChromeAction(
                                    "Delete Agent",
                                    enabled = agent != null,
                                    destructive = true,
                                    icon = R.drawable.ic_delete,
                                    group = true
                                ) { action = "Delete agent" }
                            )
                        )
                        ConversationRoute(
                            conversation,
                            sends,
                            photos = photos,
                            draft = draft,
                            seen = seen
                        ) {
                            ConversationStatusStrip(
                                watchState,
                                agent,
                                watches::select,
                                {
                                    watches.refresh(it)
                                },
                                { watches.refresh(WatchFamily.SCRIPT, more = true) },
                                { selected = it },
                                agentWatches = waiting,
                                onRetryAgents = agentWatches::refresh,
                                onPrAction = watches::prAction
                            )
                        }
                    }
                }
                watchState.selected?.let {
                    ScriptWatchSheet(it, agent?.name, watches::loadDetail, watches::dismiss)
                }
            }
        }
    }
}

/** iOS WorkspaceDetailResources: terminals, browsers and scripts that have run follow the agents. */
@Composable
private fun WorkspaceAgentResources(
    session: RpcSession,
    workspaceId: String,
    connectionId: String,
    onOpenTerminal: (String) -> Unit,
    content: @Composable (AgentListResources) -> Unit
) {
    val openLink = LocalWorkspaceBrowserLink.current
    WorkspaceTerminalsHost(session, workspaceId, onOpenTerminal) { terminals ->
        WorkspaceBrowsersHost(session, connectionId, workspaceId) { browsers ->
            WorkspaceScriptsHost(session, workspaceId, {
                openLink?.invoke(it)
            }) { scripts, actions ->
                var revealed by remember(session, workspaceId) { mutableStateOf<String?>(null) }
                val bleed = Modifier.horizontalBleed(IntentSpacing.page)
                val reveal: (String?) -> Unit = { revealed = it }
                content(
                    AgentListResources(
                        visible = terminals.state.terminals.isNotEmpty() ||
                            browsers.state.browsers.isNotEmpty() ||
                            browsers.state.error != null ||
                            scripts.rows.any { it.hasRun },
                        refresh = {
                            terminals.refresh()
                            actions.reload()
                            browsers.retry()
                        },
                        items = {
                            workspaceTerminalsSection(terminals, revealed, reveal, bleed)
                            workspaceBrowsersSection(browsers, revealed, reveal, bleed)
                            workspaceScriptsSection(scripts, actions, revealed, reveal, bleed)
                        }
                    )
                )
            }
        }
    }
}
