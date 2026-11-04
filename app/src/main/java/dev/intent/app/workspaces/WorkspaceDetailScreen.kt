package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.attachments.MediaPreview
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTabs
import dev.intent.app.design.IntentTextButton
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
    val sections =
        listOf(
            "Agents" to WorkspaceDestination.Conversations,
            "Context" to WorkspaceDestination.Notes,
            "Changes" to WorkspaceDestination.Git
        )
    val selectedIndex = sections.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    var showDetails by rememberSaveable(workspace?.id) { mutableStateOf(false) }
    Column {
        CompactAppBar("", actions = actions)
        WorkspaceHeading(workspace, onDetails = { showDetails = true })
        IntentTabs(
            titles = sections.map { it.first },
            selectedIndex = selectedIndex,
            onSelect = { open(sections[it].second) },
            modifier =
            Modifier.padding(
                horizontal = IntentSpacing.page,
                vertical = IntentSpacing.small
            )
        )
    }
    if (showDetails && workspace != null) WorkspaceSummaryDialog(workspace) { showDetails = false }
}

@Composable
private fun WorkspaceHeading(workspace: Workspace?, onDetails: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > LARGE_TEXT_SCALE
    Column(
        Modifier.padding(horizontal = IntentScreen.sectionInsets),
        verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
    ) {
        Text(
            workspace?.title ?: LocalWorkspaceTitle.current,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
            maxLines = if (largeText) 2 else 3,
            overflow = TextOverflow.Ellipsis
        )
        if (!largeText) {
            workspace?.let(::workspaceRepository)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentScreen.secondaryText
                )
            }
        }
        Text(
            workspace?.let(::workspaceStateLabel) ?: "Workspace state unavailable",
            style = MaterialTheme.typography.labelMedium,
            color = IntentScreen.secondaryText
        )
        if (!largeText && workspace != null) WorkspaceSummaryPreview(workspace)
        if (workspace != null) {
            IntentTextButton(onClick = onDetails) { Text("Workspace details") }
        }
    }
}

@Composable
private fun WorkspaceSummaryPreview(workspace: Workspace) {
    Row(horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium)) {
        workspace.statusMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        workspaceStatusImage(workspace)?.let {
            Box(Modifier.size(64.dp).clipToBounds()) {
                MediaPreview(it, actions = false, showName = false)
            }
        }
    }
}

@Composable
private fun WorkspaceSummaryDialog(workspace: Workspace, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workspace details") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
            ) {
                Text(
                    workspace.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                workspaceRepository(workspace)?.let { Text(it) }
                Text(workspaceStateLabel(workspace))
                workspacePrLabel(workspace)?.let { Text(it) }
                workspaceSignals(workspace).forEach { Text(it) }
                workspace.statusMessage?.takeIf { it.isNotBlank() }?.let { Text(it) }
                workspaceStatusImage(workspace)?.let { MediaPreview(it, showName = false) }
                workspaceActivityLabel(workspace)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { IntentTextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private const val LARGE_TEXT_SCALE = 1.3f
