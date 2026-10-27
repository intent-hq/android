package dev.intent.app.workspaces

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.intent.app.design.IntentScreen
import dev.intent.app.design.IntentTextButton as TextButton
import dev.intent.app.design.IntentTextField as OutlinedTextField

@Composable
internal fun WorkspaceActionDialog(state: WorkspaceActionState, store: WorkspaceActionStore) {
    val workspace = state.workspace ?: return
    val action = state.action ?: return
    var draft by rememberSaveable(workspace.id, action) { mutableStateOf(workspace.title) }
    AlertDialog(
        onDismissRequest = store::cancel,
        title = { Text("${action.label} workspace") },
        text = {
            Column {
                if (action == WorkspaceAction.Rename) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Workspace name") },
                        singleLine = true,
                        enabled = !state.busy
                    )
                } else {
                    Text(
                        if (action == WorkspaceAction.Archive) {
                            "Archive \"${workspace.title}\"? Active work will stop. You can unarchive it later."
                        } else {
                            "Delete \"${workspace.title}\"? This cannot be undone."
                        }
                    )
                }
                state.error?.let { Text(it, color = IntentScreen.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = { store.submit(draft) }) {
                Text(if (action == WorkspaceAction.Rename) "Save" else action.label)
            }
        },
        dismissButton = {
            TextButton(enabled = !state.busy, onClick = store::cancel) { Text("Cancel") }
        }
    )
}
