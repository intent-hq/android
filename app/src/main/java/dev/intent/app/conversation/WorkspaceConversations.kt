package dev.intent.app.conversation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.agents.AgentListRoute
import dev.intent.app.agents.AgentStore
import dev.intent.app.agents.LocalConversationAgent
import dev.intent.app.agents.NewAgentOptionsStore
import dev.intent.app.agents.NewAgentScreen
import dev.intent.app.agents.RpcAgentSession
import dev.intent.app.attachments.AndroidPhotoSource
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalChromeActions
import dev.intent.app.navigation.LocalChromeBack
import dev.intent.app.workspaces.RpcWorkspaceOperations
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.session.RpcSession

@Suppress("LongMethod") // List, creation, and conversation destinations share selection state.
@Composable
fun WorkspaceConversations(session: RpcSession, workspaceId: String, onBack: () -> Unit) {
    val listState = rememberSaveableStateHolder()
    val agents =
        remember(session, workspaceId) {
            AgentStore(RpcAgentSession(session, workspaceId), workspaceId)
        }
    var selected by rememberSaveable(session, workspaceId) { mutableStateOf<String?>(null) }
    var creating by rememberSaveable(session, workspaceId) { mutableStateOf(false) }
    val creation = remember(session) { NewAgentOptionsStore(RpcWorkspaceOperations(session)) }
    DisposableEffect(agents) { onDispose { agents.close() } }
    BackHandler(selected != null) { selected = null }
    Column {
        val agentId = selected
        if (creating) {
            NewAgentScreen(creation, workspaceId, { creating = false }) {
                creating = false
                agents.refresh()
                selected = it
            }
        } else if (agentId == null) {
            CompositionLocalProvider(
                LocalChromeBack provides onBack,
                LocalChromeActions provides
                    (
                        listOf(ChromeAction("New agent") { creating = true }) +
                            LocalChromeActions.current
                        )
            ) {
                listState.SaveableStateProvider("agents") {
                    AgentListRoute(agents, { selected = it })
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
            CompositionLocalProvider(
                LocalConversationAgent provides agentState.agents.firstOrNull {
                    it.id == agentId
                }
            ) {
                CompactAppBar(
                    agentState.agents.firstOrNull { it.id == agentId }?.name ?: "Conversation",
                    onBack = { selected = null }
                )
                ConversationRoute(conversation, sends, photos = photos)
            }
        }
    }
}
