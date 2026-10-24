package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.browser.WorkspaceBrowser
import dev.intent.app.cache.OfflineCache
import dev.intent.app.conversation.WorkspaceConversations
import dev.intent.app.git.WorkspaceGitRoute
import dev.intent.app.notes.NoteListRoute
import dev.intent.app.notes.NoteReaderRoute
import dev.intent.app.notes.SessionNoteSource
import dev.intent.app.terminals.WorkspaceTerminals
import dev.intent.core.protocol.session.RpcSession

@Composable
fun WorkspaceContent(
    session: RpcSession,
    workspaceId: String,
    cache: OfflineCache,
    onBack: () -> Unit
) {
    var destination by remember(session, workspaceId) {
        mutableStateOf<WorkspaceDestination>(WorkspaceDestination.Home)
    }
    val source = remember(session) { SessionNoteSource(session) }
    val back = {
        val parent = destination.parent()
        if (parent == null) onBack() else destination = parent
    }
    BackHandler { back() }
    when (val current = destination) {
        WorkspaceDestination.Browser -> WorkspaceBrowser(session, workspaceId, back)
        WorkspaceDestination.Git -> WorkspaceGitRoute(session, workspaceId, back)
        WorkspaceDestination.Terminals -> WorkspaceTerminals(session, workspaceId, back)
        WorkspaceDestination.Conversations -> WorkspaceConversations(session, workspaceId, back)
        else -> Column(Modifier.fillMaxSize()) {
            TextButton(onClick = back) { Text("Back") }
            when (current) {
                WorkspaceDestination.Home -> {
                    Text("Workspace")
                    TextButton(onClick = {
                        destination = WorkspaceDestination.Browser
                    }) { Text("Browser preview") }
                    TextButton(onClick = { destination = WorkspaceDestination.Terminals }) {
                        Text("Terminals")
                    }
                    TextButton(onClick = { destination = WorkspaceDestination.Git }) {
                        Text("Git changes")
                    }
                    TextButton(onClick = { destination = WorkspaceDestination.Conversations }) {
                        Text("Conversations")
                    }
                    TextButton(onClick = { destination = WorkspaceDestination.Notes }) {
                        Text("Notes")
                    }
                }
                WorkspaceDestination.Notes -> NoteListRoute(
                    session,
                    workspaceId,
                    { destination = WorkspaceDestination.Note(it) },
                    Modifier.weight(1f)
                )
                is WorkspaceDestination.Note -> NoteReaderRoute(
                    source,
                    workspaceId,
                    current.id,
                    Modifier.weight(1f),
                    cache
                )
                else -> Unit
            }
        }
    }
}
