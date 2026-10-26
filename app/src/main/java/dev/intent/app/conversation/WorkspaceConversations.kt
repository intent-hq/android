package dev.intent.app.conversation

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
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.agents.AgentActionDialog
import dev.intent.app.agents.AgentControls
import dev.intent.app.agents.AgentListRoute
import dev.intent.app.agents.AgentModelDialog
import dev.intent.app.agents.AgentStore
import dev.intent.app.agents.LocalConversationAgent
import dev.intent.app.agents.NewAgentOptionsStore
import dev.intent.app.agents.NewAgentScreen
import dev.intent.app.agents.RpcAgentSession
import dev.intent.app.attachments.AndroidPhotoSource
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.design.IntentScreen
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions
import dev.intent.app.navigation.LocalChromeBack
import dev.intent.app.workspaces.RpcWorkspaceOperations
import dev.intent.app.workspaces.recentWorkspaceAgentEntry
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.session.RpcSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LongMethod", "TooGenericExceptionCaught") // Route lifetime and RPC error presentation.
@Composable
fun WorkspaceConversations(session: RpcSession, workspaceId: String, onBack: () -> Unit) {
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
    val historyAgent = dev.intent.app.history.LocalHistoryAgent.current
    var selected by rememberSaveable(session, workspaceId) { mutableStateOf(historyAgent) }
    LaunchedEffect(historyAgent) { if (historyAgent != null) selected = historyAgent }
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
                LocalChromeBack provides onBack,
                LocalChromeActions provides
                    (
                        listOf(ChromeAction("New agent") { creating = true }) +
                            LocalChromeActions.current
                        )
            ) {
                listState.SaveableStateProvider("agents") {
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
                                        failure.message ?: "Notification setting was not saved."
                                } finally {
                                    muting = false
                                }
                            }
                        }
                    })
                }
            }
        } else {
            val conversation = remember(session, workspaceId, agentId) {
                ConversationStore(RpcConversationSession(session, workspaceId, agentId), agentId)
            }
            val sends = remember(session, workspaceId, agentId) {
                ConversationSendStore(
                    RpcConversationCommands(session, workspaceId, agentId),
                    agentId
                )
            }
            val context = LocalContext.current.applicationContext
            val photos = remember(session, workspaceId, agentId) {
                PhotoAttachmentStore(
                    AndroidPhotoSource(
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
            dev.intent.app.history.RecordAgentVisit(workspaceId, agent)
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
                LocalConversationAgent provides agentState.agents.firstOrNull {
                    it.id == agentId
                }
            ) {
                CompactAppBar(
                    agentState.agents.firstOrNull { it.id == agentId }?.name ?: "Conversation",
                    onBack = { selected = null },
                    actions = listOf(
                        "Model settings",
                        "Rename agent",
                        "Delete agent"
                    ).map { label ->
                        ChromeAction(label, enabled = agent != null) { action = label }
                    }
                )
                ConversationRoute(conversation, sends, photos = photos)
            }
        }
    }
}
