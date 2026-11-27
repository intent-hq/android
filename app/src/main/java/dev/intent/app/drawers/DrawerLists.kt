package dev.intent.app.drawers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.R
import dev.intent.app.connection.ConnectionState
import dev.intent.app.connection.displayName
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.LocalIntentDarkTheme
import dev.intent.app.history.AgentHistoryStore
import dev.intent.app.history.AgentVisit
import dev.intent.app.history.historyIdentity

@Composable
fun ConnectionsDrawer(state: ConnectionState, onSelect: (String) -> Unit) {
    // iOS ConnectionSwitcherList: an eyebrow and plain rows, centered in the drawer.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = IntentSpacing.page,
                end = SWITCHER_TRAILING_INSET,
                top = IntentSpacing.page,
                bottom = IntentSpacing.page
            ),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.page, Alignment.CenterVertically)
    ) {
        Text(
            "CONNECTIONS",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelSmall,
            color = IntentScreen.secondaryText
        )
        if (state.connections.isEmpty()) {
            Text("No saved connections.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)) {
                state.connections.forEach { connection ->
                    ConnectionSwitcherRow(
                        connection.displayName,
                        connection.id == state.activeId,
                        Modifier.testTag("drawer.connection.${connection.id}")
                    ) { onSelect(connection.id) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionSwitcherRow(
    name: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(SWITCHER_ROW_RADIUS)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SWITCHER_ROW_HEIGHT)
            .clip(shape)
            .background(if (active) switcherSelection() else Color.Transparent, shape)
            .semantics { selected = active }
            .clickable(onClick = onClick)
            .padding(IntentSpacing.content),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.content),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_connection_laptop),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Text(
            name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// iOS ConnectionSwitcherStyle.selection: charcoal in dark mode, white in light mode.
@Composable
private fun switcherSelection(): Color =
    if (LocalIntentDarkTheme.current) CharcoalGrey else Color.White

@Composable
@Suppress("LongMethod") // History header and connection-scoped selectable rows.
fun AgentHistoryDrawer(
    store: AgentHistoryStore,
    state: ConnectionState,
    onSelect: (AgentVisit) -> Unit
) {
    val currentAgent = LocalCurrentHistoryAgent.current
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
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium
                )
                if (visible.isNotEmpty()) {
                    TextButton(
                        onClick = store::clear,
                        modifier = Modifier.semantics { contentDescription = "Clear agent history" }
                    ) { Text("Clear") }
                }
            }
        }
        if (visible.isEmpty()) item { Text("Agents you open on any device appear here.") }
        items(visible, key = { "${it.connectionId}/${it.agentId}" }) { visit ->
            val current = visit.connectionId == state.activeId && visit.agentId == currentAgent
            Column(
                Modifier.fillMaxWidth().semantics {
                    selected = current
                }.testTag("history.${visit.connectionId}.${visit.agentId}")
                    .clickable { onSelect(visit) }.padding(vertical = IntentScreen.itemSpacing)
            ) {
                val identity = visit.historyIdentity(
                    state.activeId,
                    titles,
                    saved[visit.connectionId]?.displayName
                )
                Text(identity.title, style = MaterialTheme.typography.titleSmall)
                if (identity.subtitle.isNotBlank()) {
                    Text(
                        identity.subtitle,
                        color = IntentScreen.secondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                        visit.visitedAt
                    ).toString() + if (current) " · Current agent" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
    }
}

// iOS ConnectionSwitcherStyle geometry.
private val SWITCHER_TRAILING_INSET = 32.dp
private val SWITCHER_ROW_HEIGHT = 56.dp
private val SWITCHER_ROW_RADIUS = 20.dp

@Suppress("MagicNumber") // iOS DesignPalette.charcoalGrey.
private val CharcoalGrey = Color(0xFF3D3D3C)
