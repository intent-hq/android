package dev.intentapp.android.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.intent.core.protocol.attachments.AttachmentUploadService
import dev.intent.core.protocol.attachments.RpcAttachmentClient
import dev.intent.core.protocol.session.RpcSession
import dev.intent.core.protocol.session.SubscriptionSpec
import dev.intentapp.android.agents.LocalConversationAgent
import dev.intentapp.android.attachments.AndroidAttachmentSource
import dev.intentapp.android.attachments.AttachmentMedia
import dev.intentapp.android.attachments.LocalAttachmentMedia
import dev.intentapp.android.attachments.PhotoAttachmentStore
import dev.intentapp.android.conversation.ComposerDraft
import dev.intentapp.android.conversation.ConversationDraftStore
import dev.intentapp.android.conversation.ConversationRoute
import dev.intentapp.android.conversation.ConversationSeen
import dev.intentapp.android.conversation.ConversationSendStore
import dev.intentapp.android.conversation.ConversationStore
import dev.intentapp.android.conversation.RpcConversationCommands
import dev.intentapp.android.conversation.RpcConversationSession
import dev.intentapp.android.conversation.markConversationSeen
import dev.intentapp.android.conversation.scheduleSeen
import java.io.File

@Composable
fun AssistantRoute(
    session: RpcSession,
    connectionScope: String,
    onBack: () -> Unit,
    onWorkspace: (String) -> Unit
) {
    val store =
        remember(session) {
            AssistantStore({ method, params ->
                session.request(method, params)
            }, { session.serverCapabilities })
        }
    DisposableEffect(store) { onDispose { store.close() } }
    val state by store.state.collectAsState()
    DisposableEffect(session, state.owner) {
        val lease = if (state.owner) {
            session.subscribe(
                SubscriptionSpec("events.subscribe", scope(), notificationMethod = "events.event"),
                onMessage = { payload ->
                    val event = (payload["event"] ?: payload).objectValue()
                    if (event.text("type") in
                        setOf(
                            "agent:created",
                            "agent:deleted",
                            "agent:renamed",
                            "agent:retired",
                            "agent:restored"
                        )
                    ) {
                        store.refresh()
                    }
                },
                onSubscribed = { store.refresh() }
            )
        } else {
            null
        }
        onDispose { lease?.close() }
    }
    AssistantScreen(store, onBack) { id ->
        CompositionLocalProvider(
            LocalConversationAgent provides state.threads.firstOrNull { it.id == id }
        ) {
            AssistantConversation(session, id, connectionScope, onWorkspace)
        }
    }
}

@Composable
@Suppress("LongMethod") // Owns conversation resources for one thread.
private fun AssistantConversation(
    session: RpcSession,
    agentId: String,
    connectionScope: String,
    onWorkspace: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val conversation =
        remember(session, agentId) {
            ConversationStore(
                RpcConversationSession(session, ASSISTANT_WORKSPACE, agentId),
                agentId
            )
        }
    val sends =
        remember(session, agentId) {
            ConversationSendStore(
                RpcConversationCommands(session, ASSISTANT_WORKSPACE, agentId),
                agentId
            )
        }
    val photos = remember(session, agentId) {
        PhotoAttachmentStore(
            AndroidAttachmentSource(
                context,
                AttachmentUploadService(RpcAttachmentClient(session), ASSISTANT_WORKSPACE)
            )
        )
    }
    val media =
        remember(session, connectionScope) {
            AttachmentMedia(
                RpcAttachmentClient(session),
                ASSISTANT_WORKSPACE,
                File(context.cacheDir, "attachments"),
                connectionScope
            )
        }
    val draft = remember(connectionScope, agentId) {
        if (connectionScope.isBlank()) {
            ComposerDraft()
        } else {
            ConversationDraftStore(
                context,
                connectionScope
            ).draft(ASSISTANT_WORKSPACE, agentId)
        }
    }
    val proposals =
        remember(session, agentId) {
            ProposalStore(proposalRequest(session), ASSISTANT_WORKSPACE, agentId, {
                session.serverCapabilities
            })
        }
    val seen =
        remember(session, agentId) {
            ConversationSeen({
                markConversationSeen(session, ASSISTANT_WORKSPACE, agentId, it)
            }, ::scheduleSeen)
        }
    DisposableEffect(conversation) {
        onDispose {
            conversation.close()
            sends.close()
            photos.close()
            proposals.close()
        }
    }
    CompositionLocalProvider(
        LocalAttachmentMedia provides media,
        LocalProposalStore provides proposals,
        LocalOpenProposalWorkspace provides onWorkspace
    ) {
        ConversationRoute(conversation, sends, photos = photos, draft = draft, seen = seen)
    }
}
