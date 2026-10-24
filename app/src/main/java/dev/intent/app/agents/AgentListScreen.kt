package dev.intent.app.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.core.protocol.models.Agent

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
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Agents", style = MaterialTheme.typography.headlineMedium)
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onRefresh, enabled = !state.isLoading) { Text("Refresh") }
        if (!state.isLoading &&
            state.error == null &&
            state.agents.isEmpty()
        ) {
            Text("No agents yet")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.agents, key = { it.id }) { agent ->
                AgentRow(agent) { onAgentSelected(agent.id) }
            }
        }
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
