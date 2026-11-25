package dev.intent.app.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.intent.app.design.AgentAvatarState
import dev.intent.app.design.DisclosureChevron
import dev.intent.app.design.DisclosureLabel
import dev.intent.app.design.IntentColors
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTheme
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.WorkspaceSectionAppBar
import dev.intent.core.protocol.models.Agent

private const val MAX_VISIBLE_DEPTH = 5
private val DELEGATED_TEXT_INDENT = 52.dp

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

@Composable
fun AgentListScreen(
    state: AgentListState,
    onRefresh: () -> Unit,
    onAgentSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMute: ((Agent) -> Unit)? = null
) {
    var expanded by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val groups = remember(state.agents, state.retiredAgents) {
        agentGroups(state.agents + state.retiredAgents).filter { it.agents.isNotEmpty() }
    }
    fun toggle(id: String) {
        expanded = ArrayList(if (id in expanded) expanded - id else expanded + id)
    }
    val listState = rememberLazyListState()
    Column(modifier) {
        WorkspaceSectionAppBar("Agents")
        RefreshableList(state.isLoading, onRefresh) {
            if (state.isLoading && !state.hasAgents) {
                Spacer(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = IntentSpacing.page,
                        vertical = IntentSpacing.medium
                    ),
                    verticalArrangement = Arrangement.spacedBy(IntentSpacing.content)
                ) {
                    agentListFeedback(state, false, onRefresh)
                    val display = AgentGroupDisplay(expanded.toSet(), ::toggle)
                    groups.forEach { section ->
                        agentGroupItems(section, display, onAgentSelected, onMute)
                    }
                }
            }
        }
    }
}

private data class AgentGroupDisplay(val expanded: Set<String>, val toggle: (String) -> Unit)

private fun LazyListScope.agentGroupItems(
    section: AgentGroupSection,
    display: AgentGroupDisplay,
    onAgentSelected: (String) -> Unit,
    onMute: ((Agent) -> Unit)?
) {
    val groupKey = "group-${section.group.name}"
    val open = section.group == AgentGroup.FOREGROUND || groupKey in display.expanded
    if (section.group != AgentGroup.FOREGROUND) {
        item(key = groupKey) {
            AgentGroupHeader(section, open) { display.toggle(groupKey) }
        }
    }
    if (open) {
        val collapsed = section.agents.map { it.id }.toSet() - display.expanded
        items(AgentTree(section.agents).rows(collapsed), key = { it.agent.id }) { row ->
            AgentTreeItem(
                row,
                row.agent.id in display.expanded,
                onToggle = { display.toggle(row.agent.id) },
                onClick = { onAgentSelected(row.agent.id) },
                onMute = onMute
            )
        }
    }
}

@Composable
private fun AgentGroupHeader(section: AgentGroupSection, open: Boolean, onToggle: () -> Unit) {
    val label = buildString {
        append("${section.agents.size} ${section.group.title.lowercase()}")
        if (section.running > 0) append(" · ${section.running} running")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = IntentSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = IntentScreen.secondaryText
        )
        DisclosureChevron(open)
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
    Column(Modifier.padding(start = (row.depth.coerceAtMost(MAX_VISIBLE_DEPTH) * 16).dp)) {
        AgentRowMenu(row.agent, onClick, onMute)
        if (row.childCount > 0) {
            val label = buildString {
                append("+${row.childCount} delegated")
                if (row.runningChildCount > 0) append(" · ${row.runningChildCount} running")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onToggle)
                    .padding(start = DELEGATED_TEXT_INDENT, top = IntentSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DisclosureLabel(
                    label,
                    expanded,
                    style = MaterialTheme.typography.labelSmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
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
                    text = { Text(agent.muteActionLabel()) },
                    onClick = {
                        expanded = false
                        onMute.invoke(agent)
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
    val avatarState = agent.avatarState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .combinedClickable(
                onLongClick = onLongClick,
                onLongClickLabel = "Agent actions",
                onClick = onClick
            )
            .padding(vertical = IntentSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(IntentSpacing.row),
        verticalAlignment = Alignment.Top
    ) {
        AgentIdentityAvatar(agent)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
            ) {
                Text(
                    agent.name,
                    modifier = Modifier.weight(1f).alignBy(FirstBaseline),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (agent.notificationsMuted) {
                    Text(
                        "Muted",
                        modifier = Modifier.alignBy(FirstBaseline),
                        style = MaterialTheme.typography.labelSmall,
                        color = IntentScreen.secondaryText
                    )
                }
                agentRelativeTime(agent)?.let { relative ->
                    Text(
                        relative,
                        modifier = Modifier
                            .alignBy(FirstBaseline)
                            .semantics { contentDescription = "Updated $relative" },
                        style = MaterialTheme.typography.labelSmall,
                        color = IntentScreen.secondaryText,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            AgentRowSubtitle(agent, avatarState)
        }
    }
}

@Composable
private fun AgentRowSubtitle(agent: Agent, avatarState: AgentAvatarState) {
    val waitingOnWatches = avatarState == AgentAvatarState.Idle &&
        dev.intent.app.status.LocalWatchState.current.isWaiting(agent.id)
    val (text, color) = when {
        avatarState == AgentAvatarState.AttentionBlocker ||
            avatarState == AgentAvatarState.Failed ->
            agent.rowStatus() to MaterialTheme.colorScheme.error
        avatarState == AgentAvatarState.Question ||
            avatarState == AgentAvatarState.AttentionDiscussion ->
            agent.rowStatus() to IntentColors.statusAttention
        avatarState == AgentAvatarState.Waiting ->
            agent.rowStatus() to IntentScreen.secondaryText
        waitingOnWatches ->
            "Waiting on watches" to IntentScreen.secondaryText
        else -> (agent.rowPreview() ?: return) to IntentScreen.secondaryText
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun AgentListPreview() {
    IntentTheme {
        AgentListScreen(
            AgentListState(
                listOf(
                    Agent(
                        "active",
                        "Android companion",
                        lastAgentResponse = "Updated workspace detail rows."
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
