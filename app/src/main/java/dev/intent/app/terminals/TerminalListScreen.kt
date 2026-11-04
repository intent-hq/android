package dev.intent.app.terminals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentSection
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.lists.RefreshableList

@Composable
internal fun TerminalListScreen(
    state: TerminalState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit = {},
    onDelete: (String) -> Unit = {},
    onRename: (String, String) -> Unit = { _, _ -> }
) {
    var renaming by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val enabled = state.connected && !state.loading && !state.creating && state.deleting == null
    RefreshableList(state.loading, onRefresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            item {
                TextButton(onClick = onCreate, enabled = enabled) {
                    Text(if (state.creating) "Creating terminal…" else "New terminal")
                }
            }
            state.error?.let { item { Text(it, color = IntentScreen.error) } }
            state.actionError?.let { item { Text(it, color = IntentScreen.error) } }
            if (!state.loading && state.error == null && state.terminals.isEmpty()) {
                item {
                    IntentSection("No terminal sessions") {
                        Text("Create a terminal to start a shell in this workspace.")
                    }
                }
            }
            items(state.terminals, key = { it.id }) { terminal ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onSelect(terminal.id) },
                        enabled = !state.loading && state.deleting != terminal.id,
                        modifier = Modifier.weight(1f).testTag("terminal-open-${terminal.id}")
                    ) {
                        Text("${state.title(terminal)}\n${terminal.cwd}")
                    }
                    IconButton(
                        onClick = {
                            name = state.title(terminal)
                            renaming = terminal.id
                        },
                        enabled = state.deleting != terminal.id,
                        modifier = Modifier.testTag("terminal-rename-${terminal.id}")
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename terminal ${state.title(terminal)}")
                    }
                    IconButton(
                        onClick = { deleting = terminal.id },
                        enabled = enabled,
                        modifier = Modifier.testTag("terminal-delete-${terminal.id}")
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete terminal ${state.title(terminal)}"
                        )
                    }
                    if (state.deleting == terminal.id) Text("Deleting…")
                }
            }
        }
    }
    renaming?.let { id ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename terminal") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Terminal name") },
                    supportingText = { Text("Saved on this device. Leave empty to use the default name.") },
                    singleLine = true,
                    modifier = Modifier.testTag("terminal-name")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.terminals.any { it.id == id } && state.deleting != id,
                    onClick = {
                        onRename(id, name)
                        renaming = null
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { id ->
        TerminalDeleteConfirmation(
            enabled = enabled && state.terminals.any { it.id == id },
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                onDelete(id)
            }
        )
    }
}

@Composable
private fun TerminalDeleteConfirmation(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete terminal?") },
        text = { Text("This stops the shell and any commands running in it.") },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) {
                Text("Delete terminal", color = IntentScreen.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
