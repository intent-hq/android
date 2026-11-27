package dev.intentapp.android.navigation

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
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.browser.WorkspaceBrowserLinks
import dev.intentapp.android.browser.WorkspaceBrowsersRoute
import dev.intentapp.android.cache.OfflineCache
import dev.intentapp.android.conversation.WorkspaceConversations
import dev.intentapp.android.git.WorkspaceGitRoute
import dev.intentapp.android.markdown.LocalMarkdownNoteLink
import dev.intentapp.android.notes.NoteListRoute
import dev.intentapp.android.notes.NoteReaderRoute
import dev.intentapp.android.notes.SessionNoteSource
import dev.intentapp.android.scripts.WorkspaceScriptsRoute
import dev.intentapp.android.terminals.WorkspaceTerminals
import dev.intentapp.android.workspaces.LocalOpenRecentWorkspaceAgent
import dev.intentapp.android.workspaces.WorkspaceDetailScreen

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
    val connectionId = savedBrowserConnectionId(sectionScope)
    val openAgent = LocalOpenWorkspaceAgent.current
    val preferences = remember(context, sectionScope) {
        WorkspaceSectionPreferences(context, sectionScope)
    }
    val openRecent =
        LocalOpenRecentWorkspaceAgent.current ||
            dev.intentapp.android.history.LocalHistoryAgent.current != null
    val initialSection = remember(session, workspaceId, openRecent) {
        if (openRecent) WorkspaceDestination.Conversations else preferences.load(workspaceId)
    }
    var destination by rememberSaveable(
        session,
        workspaceId,
        stateSaver = workspaceDestinationSaver
    ) {
        mutableStateOf(initialSection)
    }
    var section by rememberSaveable(session, workspaceId, stateSaver = workspaceDestinationSaver) {
        mutableStateOf(initialSection)
    }
    val historyAgent = dev.intentapp.android.history.LocalHistoryAgent.current
    LaunchedEffect(historyAgent, dev.intentapp.android.history.LocalHistorySelection.current) {
        if (historyAgent != null) {
            destination = WorkspaceDestination.Conversations
            section = WorkspaceDestination.Conversations
        }
    }
    var createTerminal by remember(session, workspaceId) { mutableStateOf(false) }
    val newTerminal = {
        createTerminal = true
        destination = WorkspaceDestination.Terminals
    }
    val savedState = rememberSaveableStateHolder()
    val source = remember(session) { SessionNoteSource(session) }
    val back = {
        createTerminal = false
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
        LocalChromeActions provides (
            listOf(
                ChromeAction("Browser preview") { destination = WorkspaceDestination.Browser },
                ChromeAction("Terminals") { destination = WorkspaceDestination.Terminals },
                ChromeAction("Scripts") { destination = WorkspaceDestination.Scripts },
                ChromeAction("New terminal", run = newTerminal)
            ) + LocalChromeActions.current
            ),
        LocalMarkdownNoteLink provides { id ->
            destination = WorkspaceDestination.Note(id)
        }
    ) {
        WorkspaceBrowserLinks(session, workspaceId) {
            val openBrowser = dev.intentapp.android.browser.LocalWorkspaceBrowserLink.current
            savedState.SaveableStateProvider(destination.routeKey()) {
                when (val current = destination) {
                    WorkspaceDestination.Browser -> WorkspaceBrowsersRoute(
                        session,
                        connectionId,
                        workspaceId,
                        back
                    )
                    WorkspaceDestination.Scripts -> WorkspaceScriptsRoute(
                        session,
                        workspaceId,
                        back,
                        onOpenBrowser = { openBrowser?.invoke(it) }
                    )
                    WorkspaceDestination.Git -> WorkspaceGitRoute(session, workspaceId, back)
                    WorkspaceDestination.Terminals -> WorkspaceTerminals(
                        session,
                        workspaceId,
                        back,
                        createTerminal,
                        onCreateHandled = { createTerminal = false }
                    )
                    WorkspaceDestination.Conversations -> WorkspaceConversations(
                        session,
                        workspaceId,
                        back,
                        connectionScope = sectionScope
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
                                cache,
                                onNoteSelected = { destination = WorkspaceDestination.Note(it) },
                                onAgentSelected = noteAgentSelection(workspaceId, openAgent)
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

private fun noteAgentSelection(
    workspaceId: String,
    open: ((String, String) -> Unit)?
): ((String) -> Unit)? = open?.let { navigate ->
    { agentId -> navigate(workspaceId, agentId) }
}

@Composable
private fun savedBrowserConnectionId(fallback: String): String =
    dev.intentapp.android.history.LocalHistoryNavigation.current?.connectionId ?: fallback
