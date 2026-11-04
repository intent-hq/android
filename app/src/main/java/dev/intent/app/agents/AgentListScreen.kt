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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    var expanded by rememberSaveable(query, currentOnly) { mutableStateOf(arrayListOf<String>()) }
    val groups = remember(state.agents, state.retiredAgents, query, currentOnly) {
        agentGroups(state.agents + state.retiredAgents).map { section ->
            section.copy(agents = filterAgents(section.agents, query, currentOnly))
        }.filter { it.agents.isNotEmpty() }
    }
    val searching = query.isNotBlank() || currentOnly
    fun toggle(id: String) {
        expanded = ArrayList(if (id in expanded) expanded - id else expanded + id)
    }
    val resources = LocalWorkspaceResources.current
    Column(modifier) {
        WorkspaceSectionAppBar("Agents")
        SearchableList(
            query,
            { query = it },
            "Search agents",
            state.hasAgents,
            currentOnly,
            { currentOnly = it }
        ) { contentPadding ->
            RefreshableList(state.isLoading, onRefresh) {
                if (state.isLoading && !state.hasAgents) {
                    Spacer(Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        agentListFeedback(state, groups.isEmpty() && searching, onRefresh)
                        val display = AgentGroupDisplay(expanded.toSet(), searching, ::toggle)
                        groups.forEach { section ->
                            agentGroupItems(section, display, onAgentSelected, onMute)
                        }
                        if (resources != null) item { resources() }
                    }
                }
            }
        }
    }
}

private val AgentListState.hasAgents: Boolean
    get() = agents.isNotEmpty() || retiredAgents.isNotEmpty()

private val AgentListState.finishedLoading: Boolean
    get() = !isLoading && !retiredLoading

private data class AgentGroupDisplay(
    val expanded: Set<String>,
    val searching: Boolean,
    val toggle: (String) -> Unit
)

private fun LazyListScope.agentListFeedback(
    state: AgentListState,
    noMatches: Boolean,
    onRefresh: () -> Unit
) {
    state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    if (state.finishedLoading && !state.hasAgents && state.error == null) {
        item { Text("No agents yet") }
    }
    if (noMatches) item { Text("No matching agents. Change the search or select All.") }
    if (state.retiredLoading) item { Text("Loading retired agents…") }
    state.retiredError?.let { error ->
        item { TextButton(onClick = onRefresh) { Text("$error · Retry") } }
    }
}

private fun LazyListScope.agentGroupItems(
    section: AgentGroupSection,
    display: AgentGroupDisplay,
    onAgentSelected: (String) -> Unit,
    onMute: ((Agent) -> Unit)?
) {
    val groupKey = "group-${section.group.name}"
    val open = display.searching || section.group == AgentGroup.FOREGROUND ||
        groupKey in display.expanded
    if (section.group != AgentGroup.FOREGROUND) {
        item(key = groupKey) {
            TextButton(onClick = { display.toggle(groupKey) }) {
                Text(
                    "${if (open) "▾" else "▸"} ${section.group.title}" +
                        " · ${section.agents.size} · ${section.running} running"
                )
            }
        }
    }
    if (open) {
        val collapsed = if (display.searching) {
            emptySet()
        } else {
            section.agents.map { it.id }.toSet() - display.expanded
        }
        items(AgentTree(section.agents).rows(collapsed), key = { it.agent.id }) { row ->
            AgentTreeItem(
                row,
                display.searching || row.agent.id in display.expanded,
                onToggle = { display.toggle(row.agent.id) },
                onClick = { onAgentSelected(row.agent.id) },
                onMute = onMute
            )
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
        MaterialTheme.colorScheme.surfaceContainerLow
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
                        "${if (expanded) "▾" else "▸"} ${row.childCount} delegated · ${row.runningChildCount} running"
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
    var showName by remember(agent.id) { mutableStateOf(false) }
    Box {
        AgentRow(agent, onClick) { expanded = true }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Read full name") },
                onClick = {
                    expanded = false
                    showName = true
                }
            )
            if (onMute != null) {
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
    if (showName) {
        AlertDialog(
            onDismissRequest = { showName = false },
            title = { Text("Agent name") },
            text = { Text(agent.name, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showName = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun AgentRow(agent: Agent, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onLongClick = onLongClick,
            onLongClickLabel = "Agent actions",
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
                maxLines = 2,
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
    )?.let(::agentPreviewText)?.takeIf { it.isNotEmpty() }

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
