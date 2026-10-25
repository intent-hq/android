package dev.intent.app.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.models.Agent

private const val MAX_VISIBLE_DEPTH = 5

@Composable
fun AgentListRoute(
    store: AgentStore,
    onAgentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    AgentListScreen(state, store::refresh, onAgentSelected, modifier)
}

@Composable
fun AgentListScreen(
    state: AgentListState,
    onRefresh: () -> Unit,
    onAgentSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var collapsed by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val rows = state.tree.rows(collapsed.toSet())
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactAppBar(
            "Agents",
            actions = listOf(ChromeAction("Refresh", !state.isLoading, onRefresh))
        )
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!state.isLoading &&
            state.error == null &&
            state.agents.isEmpty()
        ) {
            Text("No agents yet")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    onClick = { onAgentSelected(row.agent.id) }
                )
            }
        }
    }
}

@Composable
private fun AgentTreeItem(
    row: AgentTreeRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
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
        AgentRow(row.agent, onClick)
    }
}

@Composable
private fun AgentRow(agent: Agent, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)) {
        AgentIdentityAvatar(agent)
        Text(agent.name, style = MaterialTheme.typography.titleMedium)
        Text(agent.status, style = MaterialTheme.typography.labelMedium)
        val preview = if (agent.lastMessageRole == "user") {
            agent.lastUserMessage
        } else {
            agent.digest ?: agent.lastAgentResponse
        }
        preview?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2) }
        if (agent.isWaitingForOtherAgents) Text("Waiting for agents")
    }
}

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
