package dev.intent.app.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.intent.app.design.IntentButton as Button
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton

@Composable
fun ConnectionScreen(
    state: ConnectionState,
    onSelect: (String) -> Unit,
    onForget: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var forgetId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier,
        contentPadding = IntentScreen.pageInsets,
        verticalArrangement = Arrangement.spacedBy(IntentScreen.sectionSpacing)
    ) {
        item {
            Button(onClick = onAdd, enabled = !state.busy) { Text("Add connection") }
        }
        items(state.connections, key = { it.id }) { connection ->
            SavedConnectionSection(
                connection,
                state,
                onSelect,
                onDetails = { detailsId = connection.id },
                onForget = { forgetId = connection.id }
            )
        }
    }
    state.connections.firstOrNull { it.id == detailsId }?.let { connection ->
        ConnectionDetails(connection, state) { detailsId = null }
    }
    state.connections.firstOrNull { it.id == forgetId }?.let { connection ->
        AlertDialog(
            onDismissRequest = { forgetId = null },
            title = { Text("Forget ${connection.displayName}?") },
            text = {
                Text(
                    "${connection.displayAddress}\n\n" +
                        "This removes the saved connection from this device. You must pair again to reconnect."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    forgetId = null
                    onForget(connection.id)
                }) { Text("Forget connection", color = IntentScreen.error) }
            },
            dismissButton = {
                TextButton(onClick = { forgetId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedConnectionSection(
    connection: SavedConnection,
    state: ConnectionState,
    onSelect: (String) -> Unit,
    onDetails: () -> Unit,
    onForget: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    IntentSection(connection.displayName) {
        Text(
            connection.displayAddress,
            style = MaterialTheme.typography.bodySmall,
            color = IntentScreen.secondaryText
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = IntentScreen.secondaryText
        ) {
            Text(
                connectionStatus(connection.id, state),
                Modifier.padding(horizontal = IntentScreen.itemSpacing),
                style = MaterialTheme.typography.labelMedium
            )
        }
        ConnectionPathBadge(state, connection.id)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelect(connection.id) }, enabled = !state.busy) {
                Text(if (state.activeId == connection.id) "Reconnect" else "Connect")
            }
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("Manage") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Connection details") },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Forget connection", color = IntentScreen.error) },
                        onClick = {
                            menuOpen = false
                            onForget()
                        }
                    )
                }
            }
        }
    }
}
