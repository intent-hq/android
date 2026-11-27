package dev.intentapp.android.terminals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.session.RpcSession
import dev.intentapp.android.R
import dev.intentapp.android.design.IntentScreen
import dev.intentapp.android.design.IntentSpacing
import dev.intentapp.android.design.ResourceMenuAction
import dev.intentapp.android.design.ResourceSwipeRow
import dev.intentapp.android.design.SectionHeadingPull
import dev.intentapp.android.design.pullNextItem

/** Owns one terminal subscription for the Agents page and its rename and delete dialogs. */
@Composable
fun WorkspaceTerminalsHost(
    session: RpcSession,
    workspaceId: String,
    onOpen: (String) -> Unit,
    content: @Composable (WorkspaceTerminalsHandle) -> Unit
) {
    val context = LocalContext.current
    val store = remember(session, workspaceId) {
        TerminalStore(
            RpcTerminalSession(session, workspaceId),
            SavedTerminalNames(context.getSharedPreferences("terminal-names", 0), workspaceId)
        )
    }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    val state by store.state.collectAsState()
    val dialogs = remember(store) { TerminalDialogState() }
    content(
        WorkspaceTerminalsHandle(
            state,
            open = onOpen,
            rename = {
                dialogs.name = state.title(it)
                dialogs.renaming = it.id
            },
            delete = { dialogs.deleting = it },
            refresh = store::refresh
        )
    )
    val actions = TerminalListActions(store::create, store::delete, store::rename)
    TerminalDialogs(state, dialogs, actions)
}

/** iOS WorkspaceMobileTerminalsView: open terminals listed below the workspace agents. */
fun LazyListScope.workspaceTerminalsSection(
    terminals: WorkspaceTerminalsHandle,
    revealed: String?,
    onReveal: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = terminals.state
    if (state.terminals.isEmpty()) return
    item(key = "terminals.heading") {
        Box(modifier.pullNextItem(SectionHeadingPull)) {
            Text(
                "TERMINALS",
                Modifier.padding(horizontal = IntentSpacing.page).semantics { heading() },
                style = MaterialTheme.typography.labelSmall,
                color = IntentScreen.secondaryText
            )
        }
    }
    items(state.terminals, key = { "terminal.${it.id}" }) { terminal ->
        Box(modifier) {
            ResourceSwipeRow(
                terminal.id,
                "terminal",
                revealed = revealed == terminal.id,
                enabled = state.deleting != terminal.id,
                onReveal = { onReveal(if (it) terminal.id else null) },
                onOpen = { if (revealed != null) onReveal(null) else terminals.open(terminal.id) },
                onDelete = {
                    onReveal(null)
                    terminals.delete(terminal.id)
                },
                menuActions = listOf(
                    ResourceMenuAction("Rename terminal", R.drawable.ic_edit) {
                        terminals.rename(terminal)
                    }
                )
            ) { TerminalResourceRow(state.title(terminal), state.connected) }
        }
    }
    state.actionError?.let { error ->
        item(key = "terminals.error") {
            Box(modifier) {
                Text(
                    error,
                    Modifier.padding(horizontal = IntentSpacing.page),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentScreen.error
                )
            }
        }
    }
}

/** iOS WorkspaceTerminalRow: terminal icon, title, and a Disconnected status when offline. */
@Composable
private fun TerminalResourceRow(title: String, connected: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.page),
        verticalAlignment = if (connected) Alignment.CenterVertically else Alignment.Top
    ) {
        Box(Modifier.size(AVATAR_SIZE), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_terminal),
                null,
                Modifier.size(ICON_SIZE),
                tint = IntentScreen.secondaryText
            )
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight(BODY_WEIGHT)
                ),
                color = IntentScreen.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!connected) {
                Text(
                    "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentScreen.secondaryText
                )
            }
        }
    }
}

private val AVATAR_SIZE = 32.dp
private val ICON_SIZE = 24.dp
private const val BODY_WEIGHT = 450
