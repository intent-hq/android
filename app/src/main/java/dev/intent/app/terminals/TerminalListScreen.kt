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
import dev.intent.core.protocol.models.TerminalInfo

private class TerminalDialogState {
    var renaming by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var deleting by mutableStateOf<String?>(null)
}

private val TerminalState.mutationsEnabled: Boolean
    get() = connected && !loading && !creating && deleting == null

@Composable
internal fun TerminalListScreen(
    state: TerminalState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    actions: TerminalListActions = TerminalListActions()
) {
    val dialogs = remember { TerminalDialogState() }
    TerminalListContent(state, onRefresh, onSelect, dialogs, actions)
    TerminalDialogs(state, dialogs, actions)
}

@Composable
private fun TerminalListContent(
    state: TerminalState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    dialogs: TerminalDialogState,
    actions: TerminalListActions
) {
    RefreshableList(state.loading, onRefresh) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = IntentScreen.pageInsets,
            verticalArrangement = Arrangement.spacedBy(IntentScreen.itemSpacing)
        ) {
            item {
                TextButton(onClick = actions.create, enabled = state.mutationsEnabled) {
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
                TerminalListRow(terminal, state, onSelect, dialogs)
            }
        }
    }
}

@Composable
private fun TerminalListRow(
    terminal: TerminalInfo,
    state: TerminalState,
    onSelect: (String) -> Unit,
    dialogs: TerminalDialogState
) {
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
                dialogs.name = state.title(terminal)
                dialogs.renaming = terminal.id
            },
            enabled = state.deleting != terminal.id,
            modifier = Modifier.testTag("terminal-rename-${terminal.id}")
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Rename terminal ${state.title(terminal)}"
            )
        }
        IconButton(
            onClick = { dialogs.deleting = terminal.id },
            enabled = state.mutationsEnabled,
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

@Composable
private fun TerminalDialogs(
    state: TerminalState,
    dialogs: TerminalDialogState,
    actions: TerminalListActions
) {
    dialogs.renaming?.let { id ->
        TerminalRenameDialog(
            name = dialogs.name,
            onNameChange = { dialogs.name = it },
            enabled = state.terminals.any { it.id == id } && state.deleting != id,
            onDismiss = { dialogs.renaming = null },
            onConfirm = {
                actions.rename(id, dialogs.name)
                dialogs.renaming = null
            }
        )
    }
    dialogs.deleting?.let { id ->
        TerminalDeleteConfirmation(
            enabled = state.mutationsEnabled && state.terminals.any { it.id == id },
            onDismiss = { dialogs.deleting = null },
            onConfirm = {
                dialogs.deleting = null
                actions.delete(id)
            }
        )
    }
}

@Composable
private fun TerminalRenameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename terminal") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Terminal name") },
                supportingText = {
                    Text("Saved on this device. Leave empty to use the default name.")
                },
                singleLine = true,
                modifier = Modifier.testTag("terminal-name")
            )
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
