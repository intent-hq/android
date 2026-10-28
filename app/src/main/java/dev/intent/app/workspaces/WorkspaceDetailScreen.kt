package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTabs
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalWorkspaceTitle
import dev.intent.app.navigation.WorkspaceDestination
import dev.intent.core.protocol.models.Workspace

@Composable
internal fun WorkspaceDetailScreen(
    workspace: Workspace?,
    selected: WorkspaceDestination = WorkspaceDestination.Conversations,
    actions: List<ChromeAction> = emptyList(),
    open: (WorkspaceDestination) -> Unit
) {
    val sections = listOf(
        "Agents" to WorkspaceDestination.Conversations,
        "Context" to WorkspaceDestination.Notes,
        "Changes" to WorkspaceDestination.Git
    )
    val selectedIndex = sections.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    Column {
        CompactAppBar(workspace?.title ?: LocalWorkspaceTitle.current, actions = actions)
        Column(Modifier.padding(horizontal = IntentScreen.sectionInsets)) {
            workspace?.statusMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                workspace?.let { workspaceStateLabel(it) } ?: "Workspace state unavailable",
                style = MaterialTheme.typography.labelMedium,
                color = IntentScreen.secondaryText
            )
        }
        IntentTabs(
            titles = sections.map { it.first },
            selectedIndex = selectedIndex,
            onSelect = { open(sections[it].second) },
            modifier = Modifier.padding(
                horizontal = IntentSpacing.page,
                vertical = IntentSpacing.content
            )
        )
    }
}

internal fun workspaceStateLabel(workspace: Workspace): String = listOfNotNull(
    workspace.status.takeIf { it.isNotBlank() },
    workspace.displayStatus?.takeIf { it.isNotBlank() }?.replace('_', ' ')
        ?.replaceFirstChar { it.titlecase() }?.replace("Pr ", "PR ")
).distinctBy { it.lowercase() }.joinToString(" · ").ifBlank { "Workspace state unavailable" }
