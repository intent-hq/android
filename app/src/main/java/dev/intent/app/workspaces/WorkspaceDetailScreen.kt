package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.attachments.MediaPreview
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
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp > configuration.screenHeightDp
    Column {
        CompactAppBar(
            if (compact) workspace?.title ?: LocalWorkspaceTitle.current else "",
            actions = actions
        )
        if (!compact) WorkspaceHeading(workspace)
        IntentTabs(
            titles = sections.map { it.first },
            selectedIndex = selectedIndex,
            onSelect = { open(sections[it].second) },
            modifier = Modifier.padding(
                start = IntentSpacing.page,
                end = IntentSpacing.page,
                top = TAB_TOP_PADDING,
                bottom = TAB_BOTTOM_PADDING
            )
        )
    }
}

@Composable
private fun WorkspaceHeading(workspace: Workspace?) {
    val largeText = LocalDensity.current.fontScale > LARGE_TEXT_SCALE
    Column(
        Modifier.padding(
            start = IntentSpacing.page,
            end = IntentSpacing.page,
            top = HEADING_TOP_PADDING,
            bottom = HEADING_BOTTOM_PADDING
        ),
        verticalArrangement = Arrangement.spacedBy(HEADING_SPACING)
    ) {
        Text(
            workspace?.title ?: LocalWorkspaceTitle.current,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
            maxLines = if (largeText) 2 else 3,
            overflow = TextOverflow.Ellipsis
        )
        workspace?.let(::workspaceRepository)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = IntentScreen.secondaryText
            )
        }
        if (workspace != null) WorkspaceSummaryPreview(workspace)
    }
}

@Composable
private fun WorkspaceSummaryPreview(workspace: Workspace) {
    val summary = workspace.statusMessage?.trim()?.takeIf { it.isNotEmpty() }
    val image = workspaceStatusImage(workspace)
    if (summary == null && image == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(HEADING_SPACING),
        verticalAlignment = Alignment.Top
    ) {
        image?.let {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) {
                MediaPreview(it, actions = false, showName = false)
            }
        }
        summary?.let {
            Text(
                it,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = IntentScreen.secondaryText
            )
        }
    }
}

private const val LARGE_TEXT_SCALE = 1.3f

// iOS WorkspaceDetailStyle heading and tab spacing.
private val HEADING_TOP_PADDING = 64.dp
private val HEADING_BOTTOM_PADDING = 32.dp
private val HEADING_SPACING = 20.dp
private val TAB_TOP_PADDING = 8.dp
private val TAB_BOTTOM_PADDING = 40.dp
