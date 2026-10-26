package dev.intent.app.workspaces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.intent.app.design.IntentLightDarkPreview
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSpacing
import dev.intent.app.design.IntentTheme
import dev.intent.app.lists.RefreshableList
import dev.intent.app.navigation.ChromeAction
import dev.intent.app.navigation.CompactAppBar
import dev.intent.core.protocol.models.Workspace

@Suppress("LongParameterList", "LongMethod")
@Composable
fun WorkspaceListRoute(
    store: WorkspaceStore,
    onWorkspaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecent: (String) -> Unit = onWorkspaceSelected,
    onAction: (Workspace, WorkspaceAction) -> Unit = { _, _ -> },
    updatingId: String? = null
) {
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    WorkspaceListScreen(
        state,
        store::refresh,
        onWorkspaceSelected,
        modifier,
        onOpenRecent,
        onAction,
        updatingId
    )
}

@Suppress("LongParameterList", "LongMethod")
@Composable
fun WorkspaceListScreen(
    state: WorkspaceListState,
    onRefresh: () -> Unit,
    onWorkspaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenRecent: (String) -> Unit = onWorkspaceSelected,
    onAction: (Workspace, WorkspaceAction) -> Unit = { _, _ -> },
    updatingId: String? = null
) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var revealedId by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = state.workspaces.filter { showArchived || !it.status.equals("Archived", true) }
    Column(modifier) {
        CompactAppBar(
            "Workspaces",
            actions = listOf(
                ChromeAction(if (showArchived) "Hide archived" else "Show archived") {
                    showArchived = !showArchived
                    revealedId = null
                }
            )
        )
        RefreshableList(state.isLoading, onRefresh) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = IntentScreen.pageInsets,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (!state.isLoading && state.error == null && rows.isEmpty()) {
                    item { Text("No workspaces yet") }
                }
                items(rows, key = { it.id }) { workspace ->
                    WorkspaceSwipeRow(
                        workspace = workspace,
                        enabled = updatingId != workspace.id,
                        revealed = revealedId == workspace.id,
                        onReveal = { revealedId = if (it) workspace.id else null },
                        onClick = { onWorkspaceSelected(workspace.id) },
                        onOpenRecent = { onOpenRecent(workspace.id) },
                        onAction = { onAction(workspace, it) }
                    ) { WorkspaceRow(workspace) }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(workspace: Workspace) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = IntentScreen.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(IntentSpacing.content),
            verticalArrangement = Arrangement.spacedBy(IntentSpacing.medium)
        ) {
            Text(
                workspace.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            workspace.statusMessage?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IntentSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(IntentSpacing.small)
            ) {
                WorkspaceBadge(workspace.status)
                workspace.displayStatus?.takeIf { it.isNotBlank() }?.let { status ->
                    val colors = MaterialTheme.colorScheme
                    val complete = status in setOf("complete", "completed", "pr_ready")
                    WorkspaceBadge(
                        status.replace('_', ' ').replaceFirstChar { it.titlecase() }
                            .replace("Pr ", "PR "),
                        container = if (complete) {
                            colors.tertiaryContainer
                        } else {
                            colors.primaryContainer
                        },
                        foreground = if (complete) {
                            colors.onTertiaryContainer
                        } else {
                            colors.onPrimaryContainer
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceBadge(
    label: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    foreground: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(color = container, contentColor = foreground, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            modifier = Modifier.padding(
                horizontal = IntentSpacing.medium,
                vertical = IntentSpacing.small
            ),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Suppress("UnusedPrivateMember")
@IntentLightDarkPreview
@Composable
private fun WorkspaceListPreview() {
    IntentTheme {
        WorkspaceListScreen(
            WorkspaceListState(
                listOf(
                    Workspace(
                        "active",
                        "Android companion",
                        statusMessage = "The workspace list is ready for review.",
                        displayStatus = "pr_ready"
                    ),
                    Workspace(
                        "archived",
                        "Previous work",
                        status = "Archived",
                        displayStatus = "complete"
                    ),
                    Workspace(
                        "idle",
                        "A workspace with a longer title that wraps onto another line",
                        displayStatus = "idle"
                    )
                ),
                isLoading = false
            ),
            onRefresh = {},
            onWorkspaceSelected = {}
        )
    }
}
