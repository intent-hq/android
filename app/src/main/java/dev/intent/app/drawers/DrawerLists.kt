package dev.intent.app.drawers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.intent.app.connection.ConnectionState
import dev.intent.app.connection.connectionStatus
import dev.intent.app.connection.displayAddress
import dev.intent.app.connection.displayName
import dev.intent.app.design.IntentScreen
import dev.intent.app.history.AgentHistoryStore
import dev.intent.app.history.AgentVisit

@Composable
fun ConnectionsDrawer(state: ConnectionState, onSelect: (String) -> Unit, onManage: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = IntentScreen.pageInsets,
        verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
    ) {
        item { Text("Connections", style = MaterialTheme.typography.titleMedium) }
        items(state.connections, key = { it.id }) { connection ->
            Column(
                Modifier.fillMaxWidth().testTag("drawer.connection.${connection.id}")
                    .clickable { onSelect(connection.id) }
                    .semantics { selected = connection.id == state.activeId }
                    .padding(vertical = IntentScreen.itemSpacing)
            ) {
                Text(connection.displayName)
                Text(connection.displayAddress, style = MaterialTheme.typography.bodySmall)
                Text(connectionStatus(connection.id, state), color = IntentScreen.secondaryText)
            }
        }
        item { TextButton(onClick = onManage) { Text("Manage connections") } }
    }
}

@Composable
fun AgentHistoryDrawer(
    store: AgentHistoryStore,
    state: ConnectionState,
    onSelect: (AgentVisit) -> Unit
) {
    val entries by store.entries.collectAsState()
    val titles = dev.intent.app.history.LocalHistoryWorkspaceTitles.current
    val saved = state.connections.associateBy { it.id }
    val visible = entries.filter { it.connectionId in saved }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = IntentScreen.pageInsets,
        verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Recent agents",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                if (visible.isNotEmpty()) TextButton(onClick = store::clear) { Text("Clear") }
            }
        }
        if (visible.isEmpty()) item { Text("Agents you open on any device appear here.") }
        items(visible, key = { "${it.connectionId}/${it.agentId}" }) { visit ->
            Column(
                Modifier.fillMaxWidth().testTag("history.${visit.connectionId}.${visit.agentId}")
                    .clickable { onSelect(visit) }.padding(vertical = IntentScreen.itemSpacing)
            ) {
                val liveTitle = titles[visit.workspaceId].takeIf {
                    visit.connectionId ==
                        state.activeId
                }
                Text(liveTitle ?: visit.workspaceTitle ?: visit.agentName)
                Text(
                    listOfNotNull(
                        saved[visit.connectionId]?.displayName,
                        visit.agentName
                    ).joinToString(" · "),
                    color = IntentScreen.secondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        visit.visitedAt
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
    }
}
