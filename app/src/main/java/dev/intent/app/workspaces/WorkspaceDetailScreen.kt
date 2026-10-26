package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.navigation.CompactAppBar
import dev.intent.app.navigation.LocalWorkspaceTitle
import dev.intent.app.navigation.WorkspaceDestination
import dev.intent.core.protocol.models.Workspace

@Composable
internal fun WorkspaceDetailScreen(workspace: Workspace?, open: (WorkspaceDestination) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CompactAppBar(workspace?.title ?: LocalWorkspaceTitle.current)
        LazyColumn(
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            item {
                IntentSection("Overview") {
                    Text(workspace?.statusMessage?.takeIf { it.isNotBlank() } ?: "No summary yet.")
                    Text(
                        workspace?.let { workspaceStateLabel(it) } ?: "Workspace state unavailable",
                        style = MaterialTheme.typography.labelMedium,
                        color = IntentScreen.secondaryText
                    )
                }
            }
            item {
                WorkspaceLink("Agents", "Conversations") {
                    open(WorkspaceDestination.Conversations)
                }
            }
            item {
                WorkspaceLink("Context", "Notes and tasks") { open(WorkspaceDestination.Notes) }
            }
            item {
                WorkspaceLink("Changes", "Files and commit history") {
                    open(WorkspaceDestination.Git)
                }
            }
            item {
                WorkspaceLink("Browser preview", "Open a workspace web app") {
                    open(WorkspaceDestination.Browser)
                }
            }
            item {
                WorkspaceLink("Terminals", "View workspace terminal sessions") {
                    open(WorkspaceDestination.Terminals)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceLink(title: String, description: String, open: () -> Unit) {
    IntentSection(title, onClick = open) {
        Text(
            description,
            color = IntentScreen.secondaryText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

internal fun workspaceStateLabel(workspace: Workspace): String = listOfNotNull(
    workspace.status.takeIf { it.isNotBlank() },
    workspace.displayStatus?.takeIf { it.isNotBlank() }?.replace('_', ' ')
        ?.replaceFirstChar { it.titlecase() }?.replace("Pr ", "PR ")
).distinctBy { it.lowercase() }.joinToString(" · ").ifBlank { "Workspace state unavailable" }
