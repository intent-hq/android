package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.intent.app.browser.WorkspaceBrowser
import dev.intent.app.cache.OfflineCache
import dev.intent.app.conversation.WorkspaceConversations
import dev.intent.app.git.WorkspaceGitRoute
import dev.intent.app.markdown.LocalMarkdownNoteLink
import dev.intent.app.notes.NoteListRoute
import dev.intent.app.notes.NoteReaderRoute
import dev.intent.app.notes.SessionNoteSource
import dev.intent.app.terminals.WorkspaceTerminals
import dev.intent.core.protocol.session.RpcSession

@Suppress("LongMethod") // Workspace destinations share the session and Markdown link handler.
@Composable
fun WorkspaceContent(
    session: RpcSession,
    workspaceId: String,
    cache: OfflineCache,
    onBack: () -> Unit
) {
    var destination by rememberSaveable(
        session,
        workspaceId,
        stateSaver = workspaceDestinationSaver
    ) {
        mutableStateOf<WorkspaceDestination>(WorkspaceDestination.Home)
    }
    val source = remember(session) { SessionNoteSource(session) }
    val back = {
        val parent = destination.parent()
        if (parent == null) onBack() else destination = parent
    }
    BackHandler { back() }
    CompositionLocalProvider(
        LocalChromeBack provides back,
        LocalChromeActions provides (
            listOf(
                ChromeAction("Browser preview") { destination = WorkspaceDestination.Browser },
                ChromeAction("Terminals") { destination = WorkspaceDestination.Terminals }
            ) + LocalChromeActions.current
            ),
        LocalMarkdownNoteLink provides { id ->
            destination = WorkspaceDestination.Note(id)
        }
    ) {
        when (val current = destination) {
            WorkspaceDestination.Browser -> WorkspaceBrowser(session, workspaceId, back)
            WorkspaceDestination.Git -> WorkspaceGitRoute(session, workspaceId, back)
            WorkspaceDestination.Terminals -> WorkspaceTerminals(session, workspaceId, back)
            WorkspaceDestination.Conversations -> WorkspaceConversations(session, workspaceId, back)
            else -> Column(Modifier.fillMaxSize()) {
                when (current) {
                    WorkspaceDestination.Home -> {
                        CompactAppBar(LocalWorkspaceTitle.current)
                        Column(
                            Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            WorkspaceGroup("Agents", "Conversations") {
                                destination = WorkspaceDestination.Conversations
                            }
                            WorkspaceGroup("Context", "Notes and tasks") {
                                destination = WorkspaceDestination.Notes
                            }
                            WorkspaceGroup("Changes", "Files and commit history") {
                                destination = WorkspaceDestination.Git
                            }
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
}

@Composable
private fun WorkspaceGroup(title: String, description: String, open: () -> Unit) {
    Card(Modifier.clickable(onClick = open)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
