package dev.intent.app.conversation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.agents.AgentListRoute
import dev.intent.app.agents.AgentStore
import dev.intent.app.agents.RpcAgentSession
import dev.intent.app.attachments.AndroidPhotoSource
import dev.intent.app.attachments.PhotoAttachmentStore
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceConversations(session: RpcSession, workspaceId: String, onBack: () -> Unit) {
    val agents =
        remember(session, workspaceId) {
            AgentStore(RpcAgentSession(session, workspaceId), workspaceId)
        }
    var selected by remember(session, workspaceId) { mutableStateOf<String?>(null) }
    DisposableEffect(agents) { onDispose { agents.close() } }
    BackHandler(selected != null) { selected = null }
    Column {
        TextButton(onClick = {
            if (selected ==
                null
            ) {
                onBack()
            } else {
                selected = null
            }
        }) { Text("Back") }
        val agentId = selected
        if (agentId == null) {
            AgentListRoute(agents, { selected = it })
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
            ConversationRoute(conversation, sends, photos = photos)
        }
    }
}
