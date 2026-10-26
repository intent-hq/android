package dev.intent.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.intent.app.browser.WorkspaceBrowser
import dev.intent.app.cache.OfflineCache
import dev.intent.app.conversation.WorkspaceConversations
import dev.intent.app.git.WorkspaceGitRoute
import dev.intent.app.markdown.LocalMarkdownNoteLink
import dev.intent.app.notes.NoteListRoute
import dev.intent.app.notes.NoteReaderRoute
import dev.intent.app.notes.SessionNoteSource
import dev.intent.app.terminals.WorkspaceTerminals
import dev.intent.app.workspaces.LocalOpenRecentWorkspaceAgent
import dev.intent.app.workspaces.WorkspaceDetailScreen
import dev.intent.core.protocol.session.RpcSession

@Suppress("LongMethod") // Workspace destinations share the session and Markdown link handler.
@Composable
fun WorkspaceContent(
    session: RpcSession,
    workspaceId: String,
    cache: OfflineCache,
    onBack: () -> Unit,
    sectionScope: String = ""
) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context, sectionScope) {
        WorkspaceSectionPreferences(context, sectionScope)
    }
    val openRecent =
        LocalOpenRecentWorkspaceAgent.current ||
            dev.intent.app.history.LocalHistoryAgent.current != null
    var destination by rememberSaveable(
        session,
        workspaceId,
        stateSaver = workspaceDestinationSaver
    ) {
        mutableStateOf(
            if (openRecent) WorkspaceDestination.Conversations else preferences.load(workspaceId)
        )
    }
    var section by rememberSaveable(session, workspaceId, stateSaver = workspaceDestinationSaver) {
        mutableStateOf(
            if (openRecent) WorkspaceDestination.Conversations else preferences.load(workspaceId)
        )
    }
    val historyAgent = dev.intent.app.history.LocalHistoryAgent.current
    LaunchedEffect(historyAgent) {
        if (historyAgent != null) {
            destination = WorkspaceDestination.Conversations
            section = WorkspaceDestination.Conversations
        }
    }
    val savedState = rememberSaveableStateHolder()
    val source = remember(session) { SessionNoteSource(session) }
    val back = {
        val parent = destination.parent(section)
        if (parent == null) onBack() else destination = parent
    }
    BackHandler { back() }
    CompositionLocalProvider(
        LocalChromeBack provides back,
        LocalWorkspaceSectionHeader provides { actions ->
            WorkspaceDetailScreen(LocalWorkspaceDetails.current, section, actions) {
                section = it
                destination = it
                preferences.save(workspaceId, it)
            }
        },
        LocalWorkspaceResources provides {
            WorkspaceResources { destination = it }
        },
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
        savedState.SaveableStateProvider(destination.routeKey()) {
            when (val current = destination) {
                WorkspaceDestination.Browser -> WorkspaceBrowser(session, workspaceId, back)
                WorkspaceDestination.Git -> WorkspaceGitRoute(session, workspaceId, back)
                WorkspaceDestination.Terminals -> WorkspaceTerminals(session, workspaceId, back)
                WorkspaceDestination.Conversations -> WorkspaceConversations(
                    session,
                    workspaceId,
                    back
                )
                else -> Column(Modifier.fillMaxSize()) {
                    when (current) {
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
}
