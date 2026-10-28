package dev.intent.app.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.design.AgentAvatarState
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.lists.RefreshableList
import dev.intent.app.lists.SearchableList
import dev.intent.app.navigation.LocalWorkspaceResources
import dev.intent.app.navigation.WorkspaceSectionAppBar
import dev.intent.core.protocol.models.Agent

private const val MAX_VISIBLE_DEPTH = 5

@Composable
fun AgentListRoute(
    store: AgentStore,
    onAgentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMute: ((Agent) -> Unit)? = null
) {
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    AgentListScreen(state, store::refresh, onAgentSelected, modifier, onMute)
}

@Suppress("LongMethod") // Declarative agent tree, filters, and workspace resource footer.
@Composable
fun AgentListScreen(
    state: AgentListState,
    onRefresh: () -> Unit,
    onAgentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMute: ((Agent) -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf("") }
    var currentOnly by rememberSaveable { mutableStateOf(false) }
    var collapsed by rememberSaveable(query, currentOnly) { mutableStateOf(arrayListOf<String>()) }
    val tree = remember(state.agents, query, currentOnly) {
        if (query.isBlank() &&
            !currentOnly
        ) {
            state.tree
        } else {
            AgentTree(filterAgents(state.agents, query, currentOnly))
        }
    }
    val rows = tree.rows(collapsed.toSet())
    val resources = LocalWorkspaceResources.current
    Column(modifier) {
        WorkspaceSectionAppBar("Agents")
        SearchableList(
            query,
            { query = it },
            "Search agents",
            state.agents.isNotEmpty(),
            currentOnly,
            { currentOnly = it }
        ) { contentPadding ->
            RefreshableList(state.isLoading, onRefresh) {
                if (state.isLoading && state.agents.isEmpty()) {
                    Spacer(Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.error?.let {
                            item { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        if (!state.isLoading && state.error == null && state.agents.isEmpty()) {
                            item { Text("No agents yet") }
                        }
                        if (rows.isEmpty() && (query.isNotBlank() || currentOnly)) {
                            item { Text("No matching agents. Change the search or select All.") }
                        }
                        items(rows, key = { it.agent.id }) { row ->
                            AgentTreeItem(
                                row,
                                row.agent.id !in collapsed,
                                onToggle = {
                                    collapsed = ArrayList(
                                        if (row.agent.id in
                                            collapsed
                                        ) {
                                            collapsed - row.agent.id
                                        } else {
                                            collapsed + row.agent.id
                                        }
                                    )
                                },
                                onClick = { onAgentSelected(row.agent.id) },
                                onMute = onMute
                            )
                        }
                        if (resources != null) item { resources() }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentTreeItem(
    row: AgentTreeRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onMute: ((Agent) -> Unit)?
) {
    val primary = row.agent.isCoordinator()
    val color = if (primary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        Modifier.padding(
            start = (row.depth.coerceAtMost(MAX_VISIBLE_DEPTH) * 16).dp
        ).background(color)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (primary) {
                Text(
                    "Coordinator",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (row.childCount > 0) {
                TextButton(onClick = onToggle) {
                    Text(
                        "${if (expanded) "▾ Hide" else "▸ Show"} ${row.childCount} delegated agents"
                    )
                }
            }
        }
        AgentRowMenu(row.agent, onClick, onMute)
    }
}

@Composable
private fun AgentRowMenu(agent: Agent, onClick: () -> Unit, onMute: ((Agent) -> Unit)?) {
    var expanded by remember(agent.id) { mutableStateOf(false) }
    Box {
        AgentRow(agent, onClick) { if (onMute != null) expanded = true }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        agent.muteActionLabel()
                    )
                },
                onClick = {
                    expanded = false
                    onMute?.invoke(agent)
                }
            )
        }
    }
}

@Composable
private fun AgentRow(agent: Agent, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onLongClick = onLongClick,
            onLongClickLabel =
            agent.muteActionLabel(),
            onClick = onClick
        ).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        AgentIdentityAvatar(agent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                agent.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                agent.rowStatus() + if (agent.notificationsMuted) " · Notifications muted" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (agent.avatarState() == AgentAvatarState.Idle &&
                dev.intent.app.status.LocalWatchState.current.isWaiting(agent.id)
            ) {
                Text(
                    "Waiting on watches",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            agent.rowPreview()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun Agent.rowStatus(): String = when (val state = avatarState()) {
    AgentAvatarState.Waiting -> "Waiting for agents"
    else -> state.label
}

internal fun Agent.rowPreview(): String? = (
    if (lastMessageRole == "user") lastUserMessage else digest ?: lastAgentResponse
    )?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun AgentListPreview() {
    MaterialTheme {
        AgentListScreen(
            AgentListState(
                listOf(
                    Agent(
                        "active",
                        "Android companion",
                        lastAgentResponse = "The agent list is under development."
                    ),
                    Agent("archived", "Previous work", status = "idle")
                ),
                isLoading = false
            ),
            onRefresh = {},
            onAgentSelected = {}
        )
    }
}

private fun Agent.muteActionLabel(): String =
    if (notificationsMuted) "Unmute notifications" else "Mute notifications"
