package dev.intent.app.workspaces

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
import dev.intent.core.protocol.models.Workspace

@Composable
fun WorkspaceListRoute(
    store: WorkspaceStore,
    onWorkspaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by store.state.collectAsState()
    DisposableEffect(store) {
        store.start()
        onDispose { store.stop() }
    }
    WorkspaceListScreen(state, store::refresh, onWorkspaceSelected, modifier)
}

@Composable
fun WorkspaceListScreen(
    state: WorkspaceListState,
    onRefresh: () -> Unit,
    onWorkspaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Workspaces", style = MaterialTheme.typography.headlineMedium)
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onRefresh, enabled = !state.isLoading) { Text("Refresh") }
        if (!state.isLoading &&
            state.error == null &&
            state.workspaces.isEmpty()
        ) {
            Text("No workspaces yet")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.workspaces, key = { it.id }) { workspace ->
                WorkspaceRow(workspace) { onWorkspaceSelected(workspace.id) }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(workspace: Workspace, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)) {
        Text(workspace.title, style = MaterialTheme.typography.titleMedium)
        Text(workspace.status, style = MaterialTheme.typography.labelMedium)
        workspace.statusMessage?.takeIf { it.isNotBlank() }?.let { Text(it) }
        workspace.displayStatus?.let { Text(it.replace('_', ' ')) }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun WorkspaceListPreview() {
    MaterialTheme {
        WorkspaceListScreen(
            WorkspaceListState(
                listOf(
                    Workspace(
                        "active",
                        "Android companion",
                        statusMessage = "The workspace list is under development."
                    ),
                    Workspace("archived", "Previous work", status = "Archived")
                ),
                isLoading = false
            ),
            onRefresh = {},
            onWorkspaceSelected = {}
        )
    }
}
